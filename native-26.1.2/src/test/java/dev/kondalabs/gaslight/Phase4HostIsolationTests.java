package dev.kondalabs.gaslight;

import java.util.*;
import dev.kondalabs.gaslight.Geometry.*;
import dev.kondalabs.gaslight.NativePortal.*;

/** Unexpected Host failures are never allowed to escape a server tick.
 * Does not claim Minecraft-runtime or remote renderer validation.
 */
public final class Phase4HostIsolationTests {
    private static final UUID ID=UUID.fromString("4aa43f08-0f34-4226-a822-f9d4110ff710");
    private static int assertions;
    private static void yes(boolean value,String message){
        assertions++;
        if(!value)throw new AssertionError(message+" test="+assertions);
    }
    private static class FaultHost implements Phase3Engine.Host {
        int ticksRead,cameras,witnesses,authority,worldOps;
        boolean cameraThrows,authorityThrows,witnessThrows,journalThrows,journalOpenThrows;
        private final NativeWorldEngine.Actor person=new NativeWorldEngine.Actor(ID,new V(0,64,0),0,0,false,false);
        private final Phase3RecoveryJournal.Store store=new Phase3RecoveryJournal.TestMemoryStore();
        public boolean singleplayer(){return true;}
        public Collection<NativeWorldEngine.Actor> players(){ticksRead++;return List.of(person);}
        public boolean loadChunk(int x,int z){worldOps++;return false;}
        public StructureEnvelope.Result inspectChunk(int x,int z){worldOps++;return StructureEnvelope.Result.UNKNOWN;}
        public boolean canExit(NativeWorldEngine.Actor p,V target){worldOps++;return false;}
        public boolean teleport(NativeWorldEngine.Actor p,V target,double yaw){worldOps++;return false;}
        public Optional<Phase3RecoveryJournal.Store> recoveryJournal(){
            if(journalThrows)throw new IllegalStateException("journal provider failure");
            if(journalOpenThrows)return Optional.of(new Phase3RecoveryJournal.Store(){
                public boolean blockedOnOpen(){throw new IllegalStateException("dirty and inaccessible");}
                public boolean begin(List<Passage> b){throw new IllegalStateException("unreachable");}
                public boolean settle(UUID u,long g){throw new IllegalStateException("unreachable");}
                public int outstanding(){return 0;}
                public String diagnostic(){return "bad journal";}
            });
            return Optional.of(store);
        }
        public Optional<Camera> camera(NativeWorldEngine.Actor person){
            cameras++;
            if(cameraThrows)throw new IllegalStateException("broken camera retrieval");
            return Optional.of(Camera.fromYawPitch(0,65.62,0,0,0,100,16./9,128));
        }
        public boolean authoritativeCamera(NativeWorldEngine.Actor person){
            authority++;
            if(authorityThrows)throw new IllegalStateException("broken authenticated camera channel");
            return true;
        }
        public Optional<Phase3CameraWitness.Witness> cameraWitness(NativeWorldEngine.Actor actor,long tick){
            witnesses++;
            if(witnessThrows)throw new IllegalStateException("bad witness transport");
            return Optional.empty();
        }
    }
    private static Phase3Engine engine(FaultHost host,boolean physical){
        return new Phase3Engine(host,new Phase3Engine.Settings(true,physical,false,1,16,128,8),0xC0FFEE);
    }
    private static void tickExceptionIsolation(){
        for(int mode=0;mode<3;mode++){
            FaultHost host=new FaultHost();
            host.cameraThrows=mode==0;
            host.authorityThrows=mode==1;
            host.witnessThrows=mode==2;
            Phase3Engine e=engine(host,mode!=0);
            for(int i=0;i<20;i++)e.tick();
            yes(e.status().ticks()==20,"failed host tick completed without propagating exception");
            yes(e.status().published()==0&&e.status().crossed()==0,"failed host cannot publish or traverse");
            yes(e.status().recentStatus().startsWith("QUARANTINED:"),"unexpected callback must quarantine");
            int inspectionCount=host.ticksRead;
            for(int i=0;i<40;i++)e.tick();
            yes(host.ticksRead==inspectionCount,"quarantined engine must never resume reading the world");
            yes(host.worldOps==0,"unverified camera never permits world operations");
            yes(e.status().ticks()==60,"logical clock continues for diagnostics even when quarantined");
        }
    }
    private static void constructionExceptionIsolation(){
        for(int mode=0;mode<2;mode++){
            FaultHost host=new FaultHost();
            host.journalThrows=mode==0;
            host.journalOpenThrows=mode==1;
            Phase3Engine e=engine(host,true);
            yes(e.status().recentStatus().startsWith("QUARANTINED:"),"no usable journal means no physical controller");
            for(int i=0;i<80;i++)e.tick();
            yes(host.ticksRead==0 && host.worldOps==0 && e.status().published()==0,
                "journal constructor failure must veto every future world operation");
        }
    }
    public static void main(String[] unused){
        tickExceptionIsolation();constructionExceptionIsolation();
        System.out.println("PHASE4_HOST_ISOLATION_PASS: "+assertions+" assertions; 5 injected fatal host failures quarantined");
    }
}
