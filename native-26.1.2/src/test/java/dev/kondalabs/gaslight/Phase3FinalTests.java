package dev.kondalabs.gaslight;

import java.nio.file.*;
import java.io.IOException;
import java.util.*;
import dev.kondalabs.gaslight.Geometry.*;
import dev.kondalabs.gaslight.NativePortal.*;

/** Final Phase 3 independent safety/property tests. No Minecraft classes. */
public final class Phase3FinalTests {
    private static long count;
    private static void yes(boolean b,String s){count++;if(!b)throw new AssertionError(s+" ["+count+"]");}
    private static final UUID OWNER=UUID.fromString("423071f1-0000-0000-0000-000000000003");
    private static Passage passage(long g){
        var a=new Surface(new V(5,128,-30),new V(1,0,0),16,384);
        var b=new Surface(new V(1_120_005,128,-1_280_030),new V(1,0,0),16,384);
        return new Passage(a,b,OWNER,g);
    }
    private static void terrainSupercover(){
        Segment diagonal=new Segment(new V2(-.3,-.6),new V2(1024.2,1024.8));
        Set<Chunk> touched=Geometry.chunksTouched(diagonal,0,500);
        yes(touched.size()>60,"diagonal crosses many chunk columns");
        Set<Chunk> sampled=new HashSet<>();
        var good=Phase3TerrainMatcher.evaluate(List.of(diagonal),80000,-90000,(a,b)->{
            yes(b.x()==a.x()+80000 && b.z()==a.z()-90000,"paired chunk offset exact");
            yes(sampled.add(a),"every crossed column scored just once");
            return .98;
        },.7,.8,500);
        yes(good.accepted(),"sufficient budget accepts good supercover");
        yes(sampled.equals(touched),"not a single touched diagonal chunk skipped");
        yes(good.sampledPairs()==touched.size(),"sample count equals actual supercover cardinality");
        yes(!Phase3TerrainMatcher.evaluate(List.of(diagonal),80000,-90000,(a,b)->1.0,.7,.8,20)
            .accepted(),"too-small global budget refuses without truncating evidence");
        // A contaminated interior chunk would have been skipped by the old
        // fixed 0/25/50/75/100 percent per-segment sampler.
        yes(!Phase3TerrainMatcher.evaluate(List.of(diagonal),80000,-90000,
            (a,b)->a.x()==27?.2:.99,.7,.8,500).accepted(),"interior terrain gap rejects route");
        Random r=new Random(0xCAFEA11L);
        for(int n=0;n<3000;n++){
            double x=r.nextInt(2000)-1000,z=r.nextInt(2000)-1000;
            Segment s=new Segment(new V2(x,z),new V2(x+30+r.nextInt(350),z+30+r.nextInt(350)));
            Set<Chunk> actual=Geometry.chunksTouched(s,0,500);
            Set<Chunk> seen=new HashSet<>();
            var result=Phase3TerrainMatcher.evaluate(List.of(s),90000,-80000,(a,b)->{
                yes(seen.add(a),"source column memoization");return 1;
            },.7,.8,500);
            yes(result.accepted()&&actual.equals(seen),"randomized supercover equivalence");
            if(actual.size()>1){
                Chunk target=actual.iterator().next();
                yes(!Phase3TerrainMatcher.evaluate(List.of(s),90000,-80000,
                    (a,b)->a.equals(target)?.4:.95,.7,.8,500).accepted(),"contaminated route refuses");
            }
        }
    }
    private static void fileJournal() throws Exception {
        Path dir=Files.createTempDirectory("gaslight-phase3-journal-test-");
        try{
            Path file=dir.resolve("recovery.bin");
            Phase3RecoveryJournal.FileStore empty=new Phase3RecoveryJournal.FileStore(file);
            boolean durable=JournalTestSupport.directorySyncAvailable(dir);
            yes(!empty.blockedOnOpen()&&empty.outstanding()==0,"clean ledger initially available");
            yes(empty.begin(List.of(passage(55)))==durable,"intent fsynced before publication or refused without directory durability");
            if(!durable)yes(empty.blockedOnOpen()&&empty.diagnostic().contains("directory durability unconfirmed"),
                "unsupported directory durability is a real publication veto");
            yes(Files.isRegularFile(file)&&Files.size(file)>32,"real journal file written");
            yes(empty.outstanding()==1,"active generation recorded");
            yes(!empty.begin(List.of(passage(55))),"duplicate generation rejected");
            Phase3RecoveryJournal.FileStore reboot=new Phase3RecoveryJournal.FileStore(file);
            yes(reboot.blockedOnOpen()&&reboot.outstanding()==1,"restart detects prior dirty record");
            yes(!reboot.begin(List.of(passage(56))),"dirty record refuses new publication");
            yes(!reboot.settle(OWNER,55),"restart cannot silently erase unconfirmed world state");
            if(durable){
                yes(empty.settle(OWNER,55),"original verified retirement settles intent");
                yes(empty.outstanding()==0,"empty after settlement");
                Phase3RecoveryJournal.FileStore clean=new Phase3RecoveryJournal.FileStore(file);
                yes(!clean.blockedOnOpen()&&clean.outstanding()==0,"clean restart permitted");
                yes(clean.begin(List.of(passage(61),passage(61).reversed())),"multi-facet bundle persisted");
            }else{
                yes(!empty.settle(OWNER,55)&&empty.outstanding()==1,"failed publication cannot silently settle retained intent");
                yes(!empty.begin(List.of(passage(61))),"failed store never retries physical publication");
            }
            byte[] bytes=Files.readAllBytes(file);
            bytes[Math.min(17,bytes.length-10)]^=0x04;
            Path corrupt=dir.resolve("corrupt.bin");
            Files.write(corrupt,bytes);
            Phase3RecoveryJournal.FileStore damaged=new Phase3RecoveryJournal.FileStore(corrupt);
            yes(damaged.blockedOnOpen(),"CRC detects tampered or truncated records");
            yes(!damaged.begin(List.of(passage(63))),"corrupt journal cannot authorize publication");
            Files.write(corrupt,new byte[]{1,2,3});
            yes(new Phase3RecoveryJournal.FileStore(corrupt).blockedOnOpen(),"truncated journal fails closed");
            Files.write(corrupt,new byte[300_000]);
            yes(new Phase3RecoveryJournal.FileStore(corrupt).blockedOnOpen(),"oversized journal rejected before reading");
            Path directoryAsFile=dir.resolve("occupied");Files.createDirectory(directoryAsFile);
            Phase3RecoveryJournal.FileStore unwritable=new Phase3RecoveryJournal.FileStore(directoryAsFile);
            yes(unwritable.blockedOnOpen(),"directory instead of file refuses publication");
            yes(!unwritable.begin(List.of(passage(67))),"unreadable journal never stages");
        }finally{
            try(var walk=Files.walk(dir)){
                walk.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.deleteIfExists(p);}catch(IOException ignored){}});
            }
        }
    }
    private static void preparedCatalog()throws Exception {
        Path dir=Files.createTempDirectory("gaslight-catalog-");
        try {
            Path path=dir.resolve("gaslight-phase3-candidates.csv");
            yes(Phase3PreparedCatalog.load(path,8).valid(),"absent catalog is clean empty list");
            Files.writeString(path,"# certified offsets still require loaded-world checks\n70000,-80000\n-90000,80000\n");
            var valid=Phase3PreparedCatalog.load(path,8);
            yes(valid.valid()&&valid.offsets().size()==2,"ordered file-backed candidate offsets parsed");
            yes(valid.offsets().get(0).equals(new Chunk(70000,-80000)),"candidate sign preserved");
            for(String invalid:List.of("0,0", "63,0", "2147483647,0", "70000,0\n70000,0",
                                       "70000,", "x,30000", "70000,30000,100", "70000 30000")){
                Files.writeString(path,invalid);
                var result=Phase3PreparedCatalog.load(path,8);
                yes(!result.valid()&&result.offsets().isEmpty(),"invalid catalog fails whole file closed");
            }
            Files.writeString(path,"70000,50000\n80000,40000\n");
            yes(!Phase3PreparedCatalog.load(path,1).valid(),"catalog honors max candidates");
            Files.writeString(path,"# no candidates\n");
            yes(Phase3PreparedCatalog.load(path,8).offsets().isEmpty(),"comments do not create phantom destinations");
        }finally{try(var walk=Files.walk(dir)){walk.sorted(Comparator.reverseOrder())
            .forEach(p->{try{Files.deleteIfExists(p);}catch(IOException ignored){}});}}
    }
    private static void generationSeparation(){
        Passage a=passage(1);
        Passage coincident=passage(2);
        yes(!Phase3GenerationGeometry.separated(List.of(a),List.of(coincident),8),
            "separate generations may not share a portal plane");
        Surface cross=new Surface(new V(5,128,-30),new V(0,0,1),16,384);
        Passage crossing=new Passage(cross,cross,OWNER,4);
        yes(!Phase3GenerationGeometry.separated(List.of(a),List.of(crossing),8),
            "perpendicular mid-segment crossing detected");
        Surface remote=new Surface(new V(1000,128,-30),new V(1,0,0),16,384);
        Passage disjoint=new Passage(remote,remote,OWNER,8);
        yes(Phase3GenerationGeometry.separated(List.of(a),List.of(disjoint),8),
            "distant route may coexist");
        Surface close=new Surface(new V(5,128,-21),new V(1,0,0),16,384);
        yes(Phase3GenerationGeometry.separated(List.of(a),
            List.of(new Passage(close,close,OWNER,9)),8),"clearance strict about >=9 blocks");
        Surface closer=new Surface(new V(5,128,-23),new V(1,0,0),16,384);
        yes(!Phase3GenerationGeometry.separated(List.of(a),
            List.of(new Passage(closer,closer,OWNER,9)),8),"too-close independent facet rejected");
        Random rng=new Random(2358432);
        for(int i=0;i<10000;i++){
            double off=1+rng.nextDouble()*1000;
            var far=new Passage(new Surface(new V(5+off,128,-30),new V(1,0,0),16,384),
                new Surface(new V(1_120_005+off,128,-1_280_030),new V(1,0,0),16,384),OWNER,6);
            boolean result=Phase3GenerationGeometry.separated(List.of(a),List.of(far),8);
            // Coplanar 16-block-wide intervals separate after 16+8 gap.
            yes(result==(off>24),"analytical clearance reference agreement");
        }
    }
    private static final class Host implements Phase3Engine.Host {
        final Phase3RecoveryJournal.TestMemoryStore store=new Phase3RecoveryJournal.TestMemoryStore();
        NativeWorldEngine.Actor actor=new NativeWorldEngine.Actor(OWNER,new V(0,64,0),0,0,false,false);
        List<Passage> last=List.of();boolean unsafe,retire=true,authoritative=true;
        int commits,retired,teleports;
        @Override public Optional<Phase3RecoveryJournal.Store> recoveryJournal(){return Optional.of(store);}
        @Override public boolean singleplayer(){return true;}
        @Override public Collection<NativeWorldEngine.Actor> players(){return List.of(actor);}
        @Override public boolean loadChunk(int x,int z){return true;}
        @Override public StructureEnvelope.Result inspectChunk(int x,int z){
            return unsafe?StructureEnvelope.Result.STRUCTURE:StructureEnvelope.Result.SAFE;
        }
        @Override public boolean canExit(NativeWorldEngine.Actor a,V p){return true;}
        @Override public boolean teleport(NativeWorldEngine.Actor a,V p,double yaw){teleports++;return true;}
        @Override public Optional<Geometry.Camera> camera(NativeWorldEngine.Actor a){
            return Optional.of(Geometry.Camera.fromYawPitch(a.position().x(),a.position().y()+1.62,a.position().z(),a.yaw(),0,100,16.0/9,128));
        }
        @Override public boolean authoritativeCamera(NativeWorldEngine.Actor a){return authoritative;}
        @Override public Optional<Phase3Engine.VerticalSpan> verticalSpan(){return Optional.of(new Phase3Engine.VerticalSpan(-64,320));}
        @Override public Optional<Phase3CameraWitness.Witness> cameraWitness(NativeWorldEngine.Actor a,long tick){
            return Optional.of(new Phase3CameraWitness.Witness(a.id(),tick,tick,tick,camera(a).orElseThrow(),true,true));
        }
        @Override public double terrainScore(Chunk a,Chunk b){return .98;}
        @Override public List<Chunk> preparedDestinationOffsets(NativeWorldEngine.Actor a){return List.of(new Chunk(70000,-80000));}
        @Override public Optional<Phase3Publication.Transaction> stagePublication(List<Passage> facets){
            last=List.copyOf(facets);
            return Optional.of(new Phase3Publication.Transaction(){
                public boolean staged(){return true;}
                public boolean commit(){commits++;return true;}
                public boolean abort(){return true;}
            });
        }
        @Override public boolean retireAtomically(List<Passage> facets){if(!retire)return false;retired++;return true;}
    }
    private static void lifecycle(){
        var cfg=new Phase3Engine.Settings(true,true,true,1,16,128,8);
        Host host=new Host();Phase3Engine e=new Phase3Engine(host,cfg,77);
        for(int i=0;i<20;i++)e.tick();
        yes(e.status().published()==1,"initial physical publication through test mock");
        yes(host.store.outstanding()==1,"journal retains active published intent");
        host.unsafe=true;
        Surface seam=host.last.get(0).entrance();
        host.actor=new NativeWorldEngine.Actor(OWNER,new V(seam.centre().x(),64,seam.centre().z()),0,0,false,false);
        for(int i=0;i<20;i++)e.tick();
        yes(e.status().published()==1&&host.commits==1,"no new generation published before old safety veto");
        yes(e.status().recentStatus().contains("QUARANTINED"),"nearby unsafe generation quarantined");
        yes(host.retired==0,"never retires unsafe seam under actor");
        for(int i=0;i<60;i++)e.tick();
        yes(e.status().logicalEpochs()==2,"quarantine disables future planning");
        yes(host.teleports==0&&e.status().crossed()==0,"quarantine prevents all crossing");
        host.retire=false;e.close();
        yes(host.store.outstanding()==1&&e.status().activeFacets()>0,"failed close retains durable intent");
        host.retire=true;
        yes(!e.retryRetireQuarantined(),"retirement retry forbidden while player is beside portal");
        host.actor=new NativeWorldEngine.Actor(OWNER,new V(0,64,0),0,0,false,false);
        yes(e.retryRetireQuarantined(),"retirement retry succeeds after player leaves corridor");
        yes(host.store.outstanding()==0,"confirmed retirement removes durable intent");
        var noJournal=new Phase3Engine(new Phase3Engine.Host(){
            @Override public boolean singleplayer(){return true;}
            @Override public Collection<NativeWorldEngine.Actor> players(){return List.of();}
            @Override public boolean loadChunk(int a,int b){return false;}
            @Override public StructureEnvelope.Result inspectChunk(int x,int z){return StructureEnvelope.Result.SAFE;}
            @Override public boolean canExit(NativeWorldEngine.Actor a,V v){return false;}
            @Override public boolean teleport(NativeWorldEngine.Actor a,V v,double yaw){return false;}
        },cfg,1);
        for(int i=0;i<20;i++)noJournal.tick();
        yes(noJournal.status().published()==0 && noJournal.status().recentStatus().contains("journal"),
            "physical engine without durable journal refuses before doing work");
    }
    public static void main(String[] args)throws Exception {
        terrainSupercover();fileJournal();preparedCatalog();generationSeparation();lifecycle();
        System.out.println("PHASE3_FINAL_PASS: "+count+" assertions");
    }
}
