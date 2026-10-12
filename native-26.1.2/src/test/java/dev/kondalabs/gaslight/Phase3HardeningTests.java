package dev.kondalabs.gaslight;

import java.util.*;
import dev.kondalabs.gaslight.Geometry.*;
import dev.kondalabs.gaslight.NativePortal.*;

/** Failure-injection, adversarial and randomized checks of Phase 3 safety contracts.
 * Deliberately does not pretend to simulate Minecraft/GPU/client reality.
 */
public final class Phase3HardeningTests {
    private static long assertions;
    private static void check(boolean result,String message){assertions++;if(!result)throw new AssertionError(message+" at "+assertions);}
    private static final UUID ID=UUID.fromString("55555555-0000-0000-0000-000000000003");
    private static Camera camera(double yaw){return Camera.fromYawPitch(0,65.62,0,yaw,0,100,16.0/9,128);}
    private static List<Passage> bundle(){
        Surface a=new Surface(new V(0,66,-30),new V(1,0,0),12,32);
        Surface b=new Surface(new V(1_500_000,66,-1_500_030),new V(1,0,0),12,32);
        return List.of(new Passage(a,b,ID,5));
    }
    private static void terrain(){
        List<Segment> segments=List.of(new Segment(new V2(0,0),new V2(120,0)),
            new Segment(new V2(120,0),new V2(200,0)));
        var good=Phase3TerrainMatcher.evaluate(segments,70000,-70000,(a,b)->{
            check(b.x()==a.x()+70000&&b.z()==a.z()-70000,"exact translated probe pair");
            return .97;
        },.7,.8,80);
        check(good.accepted()&&good.sampledPairs()>3,"samples entire physical boundary");
        check(good.minimum()>.9&&good.average()>.9,"terrain score aggregates");
        check(!Phase3TerrainMatcher.evaluate(segments,70000,1,(a,b)->a.x()==7?.2:.99,.7,.8,80)
            .accepted(),"intermediate segment mismatch rejects");
        check(!Phase3TerrainMatcher.evaluate(segments,70000,1,(a,b)->.99,.7,.8,1)
            .accepted(),"small probe budget fails closed");
        for(double bad:new double[]{Double.NaN,Double.POSITIVE_INFINITY,-.1,1.1}){
            check(!Phase3TerrainMatcher.evaluate(segments,70000,1,(a,b)->bad,.7,.8,80)
                .accepted(),"bad sample rejected: "+bad);
        }
        check(!Phase3TerrainMatcher.evaluate(segments,70000,1,(a,b)->{
            throw new IllegalStateException("unloaded");
        },.7,.8,80).accepted(),"unknown sample exceptions fail closed");
        Random random=new Random(0xBADC0FFE);
        for(int i=0;i<12000;i++){
            int n=1+random.nextInt(8);
            List<Segment> route=new ArrayList<>();
            for(int j=0;j<n;j++){
                double x=j*16.,z=(i%5)*16.;
                route.add(new Segment(new V2(x,z),new V2(x+16,z)));
            }
            int failX=random.nextInt(n);
            var r=Phase3TerrainMatcher.evaluate(route,90000,110000,
                (a,b)->a.x()==failX?.5:1,.7,.8,200);
            check(!r.accepted(),"randomized single contaminated facet veto");
        }
        var avg=Phase3TerrainMatcher.evaluate(segments,70000,1,
            (a,b)->a.x()==0?1:.74,.70,.9,80);
        check(!avg.accepted(),"minimum alone is insufficient: mean threshold");
    }
    private static void cameraWitness(){
        Camera c=camera(0);
        V3 eye=new V3(0,65.62,0);
        var accepted=new Phase3CameraWitness.Witness(ID,100,100,200,c,true,true);
        check(Phase3CameraWitness.assess(accepted,ID,201,99,eye,2,.5).accepted(),"fresh certified camera");
        check(!Phase3CameraWitness.assess(accepted,ID,201,100,eye,2,.5).accepted(),"replay veto");
        check(!Phase3CameraWitness.assess(accepted,UUID.randomUUID(),201,99,eye,2,.5).accepted(),"wrong owner veto");
        check(!Phase3CameraWitness.assess(accepted,ID,205,99,eye,2,.5).accepted(),"stale evidence veto");
        check(!Phase3CameraWitness.assess(accepted,ID,199,99,eye,2,.5).accepted(),"future receipt veto");
        check(!Phase3CameraWitness.assess(accepted,ID,201,99,new V3(10,65.62,0),2,.5).accepted(),"eye mismatch veto");
        var disconnected=new Phase3CameraWitness.Witness(ID,101,101,201,c,true,false);
        check(!Phase3CameraWitness.assess(disconnected,ID,201,100,eye,2,.5).accepted(),"no compositor attestation");
        var unknownView=new Phase3CameraWitness.Witness(ID,101,101,201,c,false,true);
        check(!Phase3CameraWitness.assess(unknownView,ID,201,100,eye,2,.5).accepted(),"missing viewports");
        Camera bad=new Camera(c.eye(),new V3(1,0,1),c.right(),c.up(),c.halfVerticalFovRadians(),c.aspect(),c.farDistance());
        check(!Phase3CameraWitness.assess(new Phase3CameraWitness.Witness(ID,105,105,201,bad,true,true),
            ID,201,100,eye,2,.5).accepted(),"bad camera basis veto");
    }
    private static void publication(){
        var original=bundle();
        var noStage=Phase3Publication.publish(original,facets->Optional.empty());
        check(!noStage.committed()&&noStage.cleanupConfirmed(),"absent stage cannot publish");
        var stageThrow=Phase3Publication.publish(original,facets->{throw new IllegalStateException();});
        check(!stageThrow.committed()&&!stageThrow.cleanupConfirmed(),"unknown staging side effects quarantined");
        var other=new Passage(original.get(0).entrance(),original.get(0).exit(),UUID.randomUUID(),5);
        check(!Phase3Publication.publish(List.of(original.get(0),other),f->Optional.empty()).committed(),
            "mixed owner bundle rejected");
        var wrongGeneration=new Passage(original.get(0).entrance(),original.get(0).exit(),ID,6);
        check(!Phase3Publication.publish(List.of(original.get(0),wrongGeneration),f->Optional.empty()).committed(),
            "mixed generations rejected");
        for(int mode=0;mode<5;mode++){
            final int variant=mode;
            final int[] abortCount={0},commits={0};
            var r=Phase3Publication.publish(original,b->Optional.of(new Phase3Publication.Transaction(){
                @Override public boolean staged(){if(variant==4)throw new IllegalArgumentException();return variant!=0;}
                @Override public boolean commit(){commits[0]++;if(variant==3)throw new IllegalArgumentException();return variant==2;}
                @Override public boolean abort(){abortCount[0]++;return variant!=1;}
            }));
            check(r.committed()==(variant==2),"publish succeeds only on full commit");
            check(r.cleanupConfirmed()!=(variant==1),"cleanup certification tracks abort");
            check(abortCount[0]==(variant==2?0:1),"abort used exactly when needed");
        }
        var abortThrows=Phase3Publication.publish(original,b->Optional.of(new Phase3Publication.Transaction(){
            public boolean staged(){return true;}
            public boolean commit(){return false;}
            public boolean abort(){throw new IllegalStateException();}
        }));
        check(!abortThrows.committed()&&!abortThrows.cleanupConfirmed(),"abort exception quarantined");
    }
    private static final class Host implements Phase3Engine.Host {
        private final Phase3RecoveryJournal.TestMemoryStore journal=new Phase3RecoveryJournal.TestMemoryStore();
        @Override public Optional<Phase3RecoveryJournal.Store> recoveryJournal(){return Optional.of(journal);}

        final UUID owner=ID;
        NativeWorldEngine.Actor player=new NativeWorldEngine.Actor(owner,new V(0,64,0),0,0,false,false);
        boolean strongCamera=true,staging=true,stageThrows=false,commit=false,abortWorks=true,retireWorks=true;
        boolean unsafe=false,poisonTerrain=false;
        int commitAttempts,retirementAttempts;
        @Override public Collection<NativeWorldEngine.Actor> players(){return List.of(player);}
        @Override public boolean singleplayer(){return true;}
        @Override public boolean loadChunk(int a,int b){return true;}
        @Override public boolean canExit(NativeWorldEngine.Actor a,V p){return true;}
        @Override public boolean teleport(NativeWorldEngine.Actor a,V p,double yaw){return true;}
        @Override public StructureEnvelope.Result inspectChunk(int x,int z){
            return unsafe?StructureEnvelope.Result.STRUCTURE:StructureEnvelope.Result.SAFE;
        }
        @Override public Optional<Camera> camera(NativeWorldEngine.Actor p){return Optional.of(Phase3HardeningTests.camera(p.yaw()));}
        @Override public boolean authoritativeCamera(NativeWorldEngine.Actor a){return strongCamera;}
        @Override public Optional<Phase3Engine.VerticalSpan> verticalSpan(){
            return Optional.of(new Phase3Engine.VerticalSpan(-64,320));
        }
        @Override public Optional<Phase3CameraWitness.Witness> cameraWitness(NativeWorldEngine.Actor a,long tick){
            if(!strongCamera)return Optional.empty();
            return Optional.of(new Phase3CameraWitness.Witness(owner,tick,tick,tick,
                Phase3HardeningTests.camera(a.yaw()),true,true));
        }
        @Override public double terrainScore(Chunk src,Chunk dst){
            if(poisonTerrain&&src.x()==-1)return .2;
            return .95;
        }
        @Override public List<Chunk> preparedDestinationOffsets(NativeWorldEngine.Actor actor){
            return List.of(new Chunk(70000,-80000));
        }
        @Override public Optional<Phase3Publication.Transaction> stagePublication(List<Passage> facets){
            if(stageThrows)throw new IllegalStateException("stage callback throws");
            if(!staging)return Optional.empty();
            return Optional.of(new Phase3Publication.Transaction(){
                public boolean staged(){return true;}
                public boolean commit(){commitAttempts++;return commit;}
                public boolean abort(){return abortWorks;}
            });
        }
        @Override public boolean retireAtomically(List<Passage> facets){retirementAttempts++;return retireWorks;}
    }
    private static void safetyGate(){
        List<Passage> pair=bundle();
        Set<Chunk> inspected=new HashSet<>();
        var pass=Phase3SafetyGate.check(pair,1,500,(x,z)->{
            check(inspected.add(new Chunk(x,z)),"no duplicate expensive inspector query");
            return StructureEnvelope.Result.SAFE;
        });
        check(pass.accepted()&&pass.checkedColumns()==inspected.size(),"both portal surfaces inspected");
        check(inspected.stream().anyMatch(c->c.x()>90000),"destination columns inspected");
        check(inspected.stream().anyMatch(c->c.x()<=0),"source columns inspected");
        check(!Phase3SafetyGate.check(pair,1,1,(x,z)->StructureEnvelope.Result.SAFE)
            .accepted(),"global budget hard veto");
        check(!Phase3SafetyGate.check(pair,1,500,(x,z)->x>90000?
            StructureEnvelope.Result.UNKNOWN:StructureEnvelope.Result.SAFE).accepted(),
            "unknown destination hard veto");
        check(!Phase3SafetyGate.check(pair,1,500,(x,z)->x==0?
            StructureEnvelope.Result.STRUCTURE:StructureEnvelope.Result.SAFE).accepted(),
            "source structure hard veto");
        check(!Phase3SafetyGate.check(pair,1,500,(x,z)->x>90000?
            StructureEnvelope.Result.USER_PROTECTED:StructureEnvelope.Result.SAFE).accepted(),
            "destination player protected veto");
        check(!Phase3SafetyGate.check(pair,1,500,(x,z)->{
            throw new IllegalStateException("metadata retrieval failed");
        }).accepted(),"inspection exceptions veto");
        check(!Phase3SafetyGate.check(pair,0,500,(x,z)->StructureEnvelope.Result.SAFE)
            .accepted(),"zero halo refused");
    }
    private static void engineIntegration(){
        var settings=new Phase3Engine.Settings(true,true,false,1,16,128,8);
        Host host=new Host();host.commit=true;
        var engine=new Phase3Engine(host,settings,0xAABBCC);
        for(int i=0;i<20;i++)engine.tick();
        check(engine.status().logicalEpochs()==1,"fixed 20 tick epoch");
        check(engine.status().planned()==1,"uses prepared destination catalog");
        check(engine.logical().orElseThrow().destinationDx()==1_120_000,"actual chosen offset is catalog destination");
        check(engine.logical().orElseThrow().destinationDz()==-1_280_000,"paired Z offset correct");
        check(engine.status().published()==1&&engine.status().activeFacets()>0,"one atomic generation published in mock");
        host.retireWorks=false;
        engine.close();
        check(engine.status().activeFacets()>0,"failed retirement retains ownership record");
        long before=engine.status().ticks();engine.tick();
        check(engine.status().ticks()==before+1&&engine.status().published()==1,"shutdown disables new activity");
        host.retireWorks=true;
        check(engine.retryRetireQuarantined(),"retirement succeeds on retry");
        check(engine.status().activeFacets()==0,"no orphaned active bundle after retry");
        Host failure=new Host();failure.abortWorks=false;
        Phase3Engine bad=new Phase3Engine(failure,settings,0xAABBCC);
        for(int i=0;i<20;i++)bad.tick();
        check(bad.status().published()==0,"failed commit not recorded as published");
        check(bad.uncertainFacets()>0&&bad.status().recentStatus().contains("QUARANTINED"),
            "failed rollback preserved as uncertain generation");
        failure.retireWorks=false;
        bad.close();
        check(bad.uncertainFacets()>0,"quarantine survives a second failed cleanup");
        failure.retireWorks=true;
        check(bad.retryRetireQuarantined()&&bad.uncertainFacets()==0,"quarantine can be repaired");
        Host untrusted=new Host();untrusted.strongCamera=false;untrusted.commit=true;
        var blocked=new Phase3Engine(untrusted,settings,0xAABBCC);
        for(int i=0;i<20;i++)blocked.tick();
        check(blocked.status().published()==0,"no camera witness blocks publication");
        Host unavailable=new Host();unavailable.staging=false;
        var absent=new Phase3Engine(unavailable,settings,0xAABBCC);
        for(int i=0;i<20;i++)absent.tick();
        check(absent.status().published()==0&&absent.uncertainFacets()==0,
            "missing atomic publication refuses without fabricating a transaction");
        blocked.close();absent.close();
    }
    public static void main(String[] args){
        terrain();cameraWitness();publication();safetyGate();engineIntegration();
        System.out.println("PHASE3_HARDENING_PASS: "+assertions+" assertions");
    }
}
