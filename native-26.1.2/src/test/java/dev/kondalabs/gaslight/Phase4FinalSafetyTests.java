package dev.kondalabs.gaslight;

import java.util.*;
import java.io.IOException;
import dev.kondalabs.gaslight.Geometry.*;
import dev.kondalabs.gaslight.NativePortal.*;
import dev.kondalabs.gaslight.Phase3DestinationProfile.*;

/** Independent final safety-policy regressions. Does not simulate Minecraft. */
public final class Phase4FinalSafetyTests {
    private static int assertions=0;
    private static final UUID ID=UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static void yes(boolean ok,String message){assertions++;if(!ok)throw new AssertionError(message+" #"+assertions);}
    private static void rejects(Runnable op,String message){
        boolean rejected=false;
        try{op.run();}catch(IllegalArgumentException expected){rejected=true;}
        yes(rejected,message);
    }
    private static void invalidGeometry(){
        double[] dangerous={Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY};
        for(double d:dangerous){
            rejects(()->Geometry.chunksTouched(new Segment(new V2(d,0),new V2(0,0)),0,128),"nonfinite segment X veto");
            rejects(()->Geometry.chunksTouched(new Segment(new V2(0,d),new V2(0,0)),0,128),"nonfinite segment Z veto");
            rejects(()->Geometry.chunksTouched(new Segment(new V2(0,0),new V2(d,0)),0,128),"nonfinite endpoint veto");
            rejects(()->Geometry.chunksTouched(new Segment(new V2(0,0),new V2(1,1)),d,128),"nonfinite chunk expansion veto");
            rejects(()->Chunk.of(new V2(d,0)),"invalid chunk conversion veto");
            rejects(()->Geometry.enclosingLoop(new V2(0,0),List.of(new V2(0,0)),d,3,16),"invalid loop radius veto");
            rejects(()->Geometry.rear(new V2(0,0),new V2(0,1),List.of(new V2(0,d)),8,2),"invalid projection veto");
            rejects(()->new InfiniteRearLine(new V2(0,0),new V2(1,0),d,0,1),"invalid analytic offset veto");
            rejects(()->Camera.fromYawPitch(0,64,0,d,0,90,16.0/9,128),"invalid yaw veto");
        }
        rejects(()->Geometry.chunksTouched(new Segment(new V2(0,0),new V2(1,1)),-1,128),"negative expansion veto");
        rejects(()->Geometry.chunksTouched(new Segment(new V2(0,0),new V2(1,1)),0,0),"zero budget veto");
        rejects(()->Geometry.chunksTouched(new Segment(new V2(30_000_001,0),new V2(30_000_002,0)),0,128),"border veto");
        rejects(()->new InfiniteRearLine(new V2(0,0),new V2(1,0),8,0,1).at(Double.POSITIVE_INFINITY),"infinity physical position veto");
        rejects(()->new Passage(new Surface(new V(0,64,0),new V(1,0,0),16,20),
            new Surface(new V(1000,64,0),new V(1,0,0),16,20),ID,-1),"negative generation veto");
        Random rnd=new Random(0x519150L);
        for(int i=0;i<5000;i++){
            double x=(rnd.nextInt(10001)-5000)*16.0;
            double z=(rnd.nextInt(10001)-5000)*16.0;
            double dx=rnd.nextInt(51)-25,dz=rnd.nextInt(51)-25;
            var touched=Geometry.chunksTouched(new Segment(new V2(x,z),new V2(x+dx,z+dz)),0,32);
            yes(!touched.isEmpty(),"valid grid-boundary segment isn't silently empty");
            yes(touched.contains(Chunk.of(new V2(x,z))),"valid segment includes source chunk");
            yes(touched.contains(Chunk.of(new V2(x+dx,z+dz))),"valid segment includes destination chunk");
        }
    }
    private static void blockedTerrain(){
        Column safe=new Column(64,Material.SOIL,Fluid.NONE,true,"minecraft:plains");
        Column noClearance=new Column(64,Material.SOIL,Fluid.NONE,false,"minecraft:plains");
        Profile allSafe=new Profile(Collections.nCopies(9,safe));
        Profile allBlocked=new Profile(Collections.nCopies(9,noClearance));
        yes(Phase3DestinationProfile.compare(allSafe,allSafe).accepted(),"safe controls still pass");
        yes(!Phase3DestinationProfile.compare(allBlocked,allBlocked).accepted(),"identical blocked landscapes forbidden");
        for(int index=0;index<9;index++){
            List<Column> altered=new ArrayList<>(allSafe.columns());
            altered.set(index,noClearance);
            Profile blocked=new Profile(altered);
            yes(!Phase3DestinationProfile.compare(allSafe,blocked).accepted(),"single blocked destination column forbidden");
            yes(!Phase3DestinationProfile.compare(blocked,allSafe).accepted(),"single blocked source column forbidden");
        }
    }
    private static class Host implements Phase3Engine.Host {
        private final UUID owner=ID;
        int calls;
        boolean playersThrow,worldThrow,nullPeople,invalidActor;
        @Override public boolean singleplayer(){if(worldThrow)throw new IllegalStateException("backend unavailable");return true;}
        @Override public Collection<NativeWorldEngine.Actor> players(){
            if(playersThrow)throw new IllegalStateException("player metadata unavailable");
            if(nullPeople)return null;
            if(invalidActor)return List.of(new NativeWorldEngine.Actor(owner,new V(Double.NaN,64,0),0,0,false,false));
            return List.of(new NativeWorldEngine.Actor(owner,new V(0,64,0),0,0,false,false));
        }
        @Override public boolean loadChunk(int x,int z){calls++;return false;}
        @Override public StructureEnvelope.Result inspectChunk(int x,int z){calls++;return StructureEnvelope.Result.UNKNOWN;}
        @Override public boolean canExit(NativeWorldEngine.Actor player,V exit){calls++;return false;}
        @Override public boolean teleport(NativeWorldEngine.Actor player,V exit,double yaw){calls++;return false;}
        @Override public Optional<Phase3RecoveryJournal.Store> recoveryJournal(){return Optional.of(new Phase3RecoveryJournal.TestMemoryStore());}
        @Override public Optional<Camera> camera(NativeWorldEngine.Actor actor){calls++;return Optional.empty();}
    }
    private static void hostFaultIsolation(){
        for(int mode=0;mode<4;mode++){
            Host host=new Host();
            if(mode==0)host.playersThrow=true;
            if(mode==1)host.worldThrow=true;
            if(mode==2)host.nullPeople=true;
            if(mode==3)host.invalidActor=true;
            var engine=new Phase3Engine(host,new Phase3Engine.Settings(true,true,true,1,12,96,8),123);
            for(int i=0;i<100;i++)engine.tick();
            yes(engine.status().published()==0,"unavailable host can never publish");
            yes(engine.status().crossed()==0,"unavailable host can never traverse");
            yes(engine.status().recentStatus().contains("QUARANTINED"),"host data failures permanently quarantine activity");
            yes(host.calls==0,"fail before any host world interaction");
        }
    }
    private static void corruptPacketFuzz(){
        Random r=new Random(0xBEEF2112);
        for(int i=0;i<2500;i++){
            byte[] input=new byte[r.nextInt(400)];
            r.nextBytes(input);
            try{PortalSnapshotWire.decode(input);
                throw new AssertionError("random garbage decoded as a signed preview message");
            }catch(IOException expected){assertions++;}
        }
    }
    public static void main(String[] args){
        invalidGeometry();blockedTerrain();hostFaultIsolation();corruptPacketFuzz();
        System.out.println("PHASE4_FINAL_SAFETY_PASS: assertions="+assertions+
            " malformed_packets=2500 finite_geometry_checks=10000 hostile_hosts=4");
    }
}
