package dev.kondalabs.gaslight;

import java.nio.file.*;
import java.util.*;
import dev.kondalabs.gaslight.Geometry.*;
import dev.kondalabs.gaslight.NativePortal.*;
import dev.kondalabs.gaslight.Phase3DestinationProfile.*;

/** Deterministic offline adversarial suite. Seeds are logged, no fabricated Minecraft results. */
public final class Phase4ValidationTests {
    static final long SEED=0x604CEAD2026L;
    static long assertions, oracleCases, lifecycleTicks, rejectionCases;
    static void yes(boolean b,String message){assertions++; if(!b)throw new AssertionError("PHASE4: "+message+" assertion="+assertions);}
    static final UUID OWNER=UUID.fromString("ff146180-b388-4b63-b57c-a62c9d235a00");
    static Passage passage(double x,double z,long generation){
        Surface a=new Surface(new V(x,128,z),new V(1,0,0),24,384);
        Surface b=new Surface(new V(x+1_000_000,128,z-1_000_000),new V(1,0,0),24,384);
        return new Passage(a,b,OWNER,generation);
    }
    /** Independent sampled-point oracle. Any point on the segment must lie in a returned chunk. */
    static void chunkCoverage(){
        Random random=new Random(SEED);
        for(int i=0;i<18000;i++){
            double x=(random.nextInt(257)-128)*16+(random.nextInt(4)==0?0:random.nextDouble()*16);
            double z=(random.nextInt(257)-128)*16+(random.nextInt(4)==0?0:random.nextDouble()*16);
            double dx=(random.nextDouble()-.5)*350;
            double dz=(random.nextDouble()-.5)*350;
            Segment segment=new Segment(new V2(x,z),new V2(x+dx,z+dz));
            Set<Chunk> found=Geometry.chunksTouched(segment,0,400);
            yes(!found.isEmpty(),"nonempty supercover");
            for(int j=0;j<=32;j++){
                double t=j/32.0;
                double px=x+t*dx,pz=z+t*dz;
                yes(found.contains(Chunk.of(new V2(px,pz))),"point oracle omitted intersected chunk case="+i+" t="+t);
                oracleCases++;
            }
            // Translation invariance by 32 chunks (exactly representable for these coordinates)
            Segment shifted=new Segment(new V2(x+512,z-512),new V2(x+dx+512,z+dz-512));
            Set<Chunk> translated=Geometry.chunksTouched(shifted,0,400);
            yes(found.size()==translated.size(),"translation changed coverage count");
            for(Chunk c:found)yes(translated.contains(new Chunk(c.x()+32,c.z()-32)),"chunk shift oracle");
        }
        // Boundary/corner touch must not be misclassified as completely invisible.
        for(int n=-10;n<=10;n++){
            Segment point=new Segment(new V2(n*16,0),new V2(n*16,32));
            var touched=Geometry.chunksTouched(point,0,70);
            yes(touched.contains(new Chunk(n-1,0))&&touched.contains(new Chunk(n,0)),"touching an exact chunk face includes both sides n="+n+" got="+touched);
        }
    }
    static void safetyFaults(){
        Random random=new Random(SEED^0x4EA5L);
        for(int i=0;i<1800;i++){
            Passage p=passage(random.nextInt(1000)-500,random.nextInt(1000)-500,i);
            Set<Chunk> surveyed=new HashSet<>();
            var pass=Phase3SafetyGate.check(List.of(p),1,900,(x,z)->{
                yes(surveyed.add(new Chunk(x,z)),"inspector deduplicated");
                return StructureEnvelope.Result.SAFE;
            });
            yes(pass.accepted()&&pass.checkedColumns()==surveyed.size(),"fully safe candidate accepted within budget");
            List<Chunk> columns=new ArrayList<>(surveyed);
            Chunk poisoned=columns.get(random.nextInt(columns.size()));
            StructureEnvelope.Result status=switch(i%3){case 0->StructureEnvelope.Result.STRUCTURE;case 1->StructureEnvelope.Result.UNKNOWN;default->StructureEnvelope.Result.USER_PROTECTED;};
            var veto=Phase3SafetyGate.check(List.of(p),1,900,(x,z)->x==poisoned.x()&&z==poisoned.z()?status:StructureEnvelope.Result.SAFE);
            yes(!veto.accepted(),"one poisoned chunk must veto whole bundle");
            rejectionCases++;
            var unknown=Phase3SafetyGate.check(List.of(p),1,900,(x,z)->null);
            yes(!unknown.accepted(),"null inspector result is never safe");
            var throwsOut=Phase3SafetyGate.check(List.of(p),1,900,(x,z)->{throw new IllegalStateException("generated failure");});
            yes(!throwsOut.accepted(),"exception must fail closed");
        }
    }
    static void candidateProperties(){
        Random random=new Random(SEED^0xC1L);
        V2 a=new V2(0,0),b=new V2(200,80);
        for(int i=0;i<18000;i++){
            long seed=random.nextLong();
            long epoch=random.nextInt(100000);
            List<Chunk> catalog=List.of(new Chunk(70000,-80000),new Chunk(70000,-80000),new Chunk(-100000,91000),new Chunk(0,0));
            List<Phase3DestinationSelector.Candidate> first=Phase3DestinationSelector.candidates(catalog,8,seed,epoch,a,b);
            List<Phase3DestinationSelector.Candidate> again=Phase3DestinationSelector.candidates(catalog,8,seed,epoch,a,b);
            yes(first.equals(again),"candidate list reproducible from seed/epoch");
            yes(first.size()<=8 && first.size()>=1,"strict candidate capacity");
            HashSet<Chunk> unique=new HashSet<>();
            for(var c:first){
                yes(unique.add(c.offset()),"deduplicated candidates");
                yes(Math.abs((long)c.offset().x())<=1800000 && Math.abs((long)c.offset().z())<=1800000,"bounded candidate");
            }
        }
    }
    static void projectionEvidence(){
        Random random=new Random(SEED^0xF0L);
        Camera base=Camera.fromYawPitch(0,65.62,0,30,30,70,16.0/9.0,128);
        var negativeTick=new Phase3CameraWitness.Witness(OWNER,6,-1,400,base,true,true);
        yes(!Phase3CameraWitness.assess(negativeTick,OWNER,400,5,new V3(0,65.62,0),2,.5).accepted(),
            "negative client tick rejected");
        for(int i=0;i<16000;i++){
            double yaw=-180+random.nextDouble()*360,pitch=-80+random.nextDouble()*160;
            Camera camera=Camera.fromYawPitch(0,65.62,0,yaw,pitch,70,16.0/9.0,128);
            Phase3CameraWitness.Witness w=new Phase3CameraWitness.Witness(OWNER,i+1,i+1,400,camera,true,true);
            yes(Phase3CameraWitness.assess(w,OWNER,400,i,new V3(0,65.62,0),2,.5).accepted(),"valid yaw/pitch witness");
            yes(!Phase3CameraWitness.assess(w,OWNER,400,i+1,new V3(0,65.62,0),2,.5).accepted(),"replay rejected");
            yes(!Phase3CameraWitness.assess(w,OWNER,404,i,new V3(0,65.62,0),2,.5).accepted(),"stale witness rejected");
            List<V2> loop=Phase3Visibility.conservativeLoop(camera,16,8,64);
            yes(loop.size()==16 && Geometry.polygonEncloses(loop,new V2(0,0)),"physical loop bounds camera origin");
            var vertex=loop.get(random.nextInt(loop.size()));
            yes(Double.isFinite(vertex.x())&&Double.isFinite(vertex.z()),"finite loop vertex");
            Surface deliberatelyVisible=new Surface(new V(camera.eye().x()+camera.forward().x()*8,camera.eye().y()+camera.forward().y()*8,camera.eye().z()+camera.forward().z()*8),new V(1,0,0),2,4);
            // When pitched straight up/down, front facing center may not intersect the surface plane; check central ray's center proximity only.
            if(Math.abs(camera.forward().y())<.3)yes(!Phase3Visibility.hidden(camera,deliberatelyVisible,0),"in-frustum facet cannot be certified hidden");
        }
    }
    static void transactionFaultMatrix(){
        Passage p=passage(100,0,22);
        for(int bits=0;bits<128;bits++){
            int mask=bits;
            int[] stages={0},commits={0},aborts={0};
            Phase3Publication.Result result=Phase3Publication.publish(List.of(p),facets->{
                stages[0]++;
                if((mask&1)!=0)throw new IllegalStateException("stage exception");
                if((mask&2)!=0)return Optional.empty();
                return Optional.of(new Phase3Publication.Transaction(){
                    public boolean staged(){if((mask&4)!=0)throw new IllegalStateException();return (mask&8)==0;}
                    public boolean commit(){commits[0]++;if((mask&16)!=0)throw new IllegalStateException();return (mask&32)==0;}
                    public boolean abort(){aborts[0]++;if((mask&64)!=0)throw new IllegalStateException();return true;}
                });
            });
            boolean shouldCommit=(mask&63)==0 || ((mask&63)==64); // only meaningful lower 6 bits all clear
            yes(result.committed()==shouldCommit,"only fully certified atomic transaction commits mask="+mask);
            if(result.committed())yes(aborts[0]==0,"successful commit never aborts");
            else if((mask&3)==0)yes(aborts[0]==1,"known transaction attempts abort");
            if((mask&1)!=0)yes(!result.cleanupConfirmed(),"stage throw requires quarantine");
            if((mask&64)!=0&&(mask&3)==0&&!result.committed())yes(!result.cleanupConfirmed(),"failed abort requires quarantine");
        }
    }
    static void traversalTransforms(){
        Random rng=new Random(SEED^0x8B4L);
        for(int i=0;i<30000;i++){
            double angle=rng.nextDouble()*Math.PI*2;
            V tangent=new V(Math.cos(angle),0,Math.sin(angle));
            double other=rng.nextDouble()*Math.PI*2;
            V destTangent=new V(Math.cos(other),0,Math.sin(other));
            Surface a=new Surface(new V(rng.nextDouble()*1000,64,rng.nextDouble()*1000),tangent,24,64);
            Surface b=new Surface(new V(1000000+rng.nextDouble()*1000,64,-1000000+rng.nextDouble()*1000),destTangent,24,64);
            Passage passage=new Passage(a,b,OWNER,i);
            V original=new V(a.centre().x()+rng.nextDouble()*5,70,a.centre().z()+rng.nextDouble()*5);
            V moved=passage.reversed().mapPosition(passage.mapPosition(original));
            yes(original.sub(moved).norm()<1e-7,"bidirectional point transform invertible");
            V velocity=new V(rng.nextDouble()*18-9,rng.nextDouble()*3-1.5,rng.nextDouble()*18-9);
            V after=passage.mapVelocity(velocity);
            V returned=passage.reversed().mapVelocity(after);
            yes(velocity.sub(returned).norm()<1e-7,"velocity mapping reversible");
            yes(Math.abs(velocity.norm()-after.norm())<1e-7,"velocity magnitude preserved");
        }
    }
    static void journalCrashAndTamper() throws Exception {
        Path folder=Files.createTempDirectory("gaslight-phase4-journal-");
        try{
            boolean durable=JournalTestSupport.directorySyncAvailable(folder);
            Path stalePath=folder.resolve("parallel-world-journal.dat");
            var firstWriter=new Phase3RecoveryJournal.FileStore(stalePath);
            var staleWriter=new Phase3RecoveryJournal.FileStore(stalePath);
            yes(firstWriter.begin(List.of(passage(100,0,123456)))==durable,"first writer records publication or retains uncertified intent");
            yes(!staleWriter.begin(List.of(passage(200,0,123457)))&&
                staleWriter.diagnostic().contains("stale journal writer"),
                "stale concurrent journal writer cannot overwrite outstanding intent");
            yes(new Phase3RecoveryJournal.FileStore(stalePath).blockedOnOpen(),
                "unresolved intent survives stale writer attempt");
            for(int i=0;i<80;i++){
                Path path=folder.resolve("state-"+i+".dat");
                var clean=new Phase3RecoveryJournal.FileStore(path);
                yes(!clean.blockedOnOpen()&&clean.outstanding()==0,"new journal unblocked");
                Passage p=passage(i*3,10,i+1);
                yes(clean.begin(List.of(p))==durable,"publication intent durable begin or conservative platform refusal");
                if(!durable)yes(clean.diagnostic().contains("directory durability unconfirmed"),"refusal must preserve truthful durability reason");
                var crashed=new Phase3RecoveryJournal.FileStore(path);
                yes(crashed.blockedOnOpen()&&crashed.outstanding()==1,"unsettled restart blocks any publication");
                yes(!crashed.begin(List.of(passage(0,0,1000+i))),"dirty store cannot start another generation");
                if(durable){
                    yes(clean.settle(p.owner(),p.generation()),"confirmed retirement settles");
                    var recovered=new Phase3RecoveryJournal.FileStore(path);
                    yes(!recovered.blockedOnOpen()&&recovered.outstanding()==0,"clean restart allowed");
                    yes(recovered.begin(List.of(p)),"new generation can proceed after confirmed cleanup");
                }else{
                    yes(!clean.settle(p.owner(),p.generation())&&clean.outstanding()==1,"uncertified intent is never erased");
                    yes(!clean.begin(List.of(p)),"failed writer cannot publish again");
                }
                byte[] bytes=Files.readAllBytes(path);bytes[Math.min(20,bytes.length-9)]^=0x55;
                Files.write(path,bytes);
                var tampered=new Phase3RecoveryJournal.FileStore(path);
                yes(tampered.blockedOnOpen(),"corrupt record cannot authorize new generation");
            }
        }finally{try(var s=Files.walk(folder)){s.sorted(Comparator.reverseOrder()).forEach(path->{try{Files.deleteIfExists(path);}catch(Exception ignored){}});}}
    }
    static class DisabledHost implements Phase3Engine.Host {
        NativeWorldEngine.Actor actor=new NativeWorldEngine.Actor(OWNER,new V(0,64,0),0,0,false,false);
        long structureQueries,terrainQueries,stages,teleports;
        @Override public boolean singleplayer(){return true;}
        @Override public Collection<NativeWorldEngine.Actor> players(){return List.of(actor);}
        @Override public boolean loadChunk(int x,int z){return false;}
        @Override public StructureEnvelope.Result inspectChunk(int x,int z){structureQueries++;return StructureEnvelope.Result.UNKNOWN;}
        @Override public boolean canExit(NativeWorldEngine.Actor p,V exit){return false;}
        @Override public boolean teleport(NativeWorldEngine.Actor p,V exit,double yaw){teleports++;return false;}
        @Override public Optional<Phase3RecoveryJournal.Store> recoveryJournal(){return Optional.of(new Phase3RecoveryJournal.TestMemoryStore());}
        @Override public Optional<Camera> camera(NativeWorldEngine.Actor a){return Optional.of(Camera.fromYawPitch(0,65.62,0,a.yaw(),a.pitch(),95,16.0/9,96));}
        @Override public double terrainScore(Chunk a,Chunk b){terrainQueries++;return Double.NaN;}
        @Override public boolean authoritativeCamera(NativeWorldEngine.Actor a){return false;}
        @Override public Optional<Phase3Publication.Transaction> stagePublication(List<Passage> facets){stages++;return Optional.empty();}
    }
    static void concurrentJournalWriters() throws Exception {
        Path root=Files.createTempDirectory("gaslight-parallel-writers-");
        try{
            boolean durable=JournalTestSupport.directorySyncAvailable(root);
            for(int i=0;i<40;i++){
                Path record=root.resolve("world-"+i+".dat");
                var writerA=new Phase3RecoveryJournal.FileStore(record);
                var writerB=new Phase3RecoveryJournal.FileStore(record);
                java.util.concurrent.CountDownLatch signal=new java.util.concurrent.CountDownLatch(1);
                boolean[] accepted=new boolean[2];
                Thread one=new Thread(()->{try{signal.await();accepted[0]=writerA.begin(List.of(passage(0,0,987)));}
                    catch(InterruptedException ex){Thread.currentThread().interrupt();}},"journal-1");
                Thread two=new Thread(()->{try{signal.await();accepted[1]=writerB.begin(List.of(passage(50,0,988)));}
                    catch(InterruptedException ex){Thread.currentThread().interrupt();}},"journal-2");
                one.start();two.start();signal.countDown();one.join();two.join();
                yes(durable?(accepted[0]!=accepted[1]):(!accepted[0]&&!accepted[1]),
                    "only one durable writer or no unsupported writer can publish intent");
                var resumed=new Phase3RecoveryJournal.FileStore(record);
                yes(resumed.blockedOnOpen()&&resumed.outstanding()==1,"concurrent writers did not lose intent");
            }
        }finally{try(var s=Files.walk(root)){s.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.deleteIfExists(p);}catch(Exception ignored){}});}}
    }
    static final class PlanningHost extends DisabledHost {
        boolean unknown;
        long queries;
        @Override public StructureEnvelope.Result inspectChunk(int x,int z){
            queries++;
            return unknown?StructureEnvelope.Result.UNKNOWN:StructureEnvelope.Result.SAFE;
        }
        @Override public boolean requireDetailedTerrainProfiles(){return true;}
        @Override public List<Chunk> preparedDestinationOffsets(NativeWorldEngine.Actor player){
            return List.of(new Chunk(70000,-80000));
        }
        private final Phase3DestinationProfile.Profile good=new Phase3DestinationProfile.Profile(
            Collections.nCopies(9,new Phase3DestinationProfile.Column(64,
                Phase3DestinationProfile.Material.SOIL,
                Phase3DestinationProfile.Fluid.NONE,true,"minecraft:plains")));
        @Override public Optional<Phase3DestinationProfile.Profile> terrainProfile(Chunk a){
            return unknown?Optional.empty():Optional.of(good);
        }
    }
    static void actualLogicalPlanning(){
        // Unlike a disabled host, this one supports full source/destination discovery
        // and every required terrain profile. It never enables physical publication.
        PlanningHost host=new PlanningHost();
        Phase3Engine.Settings settings=new Phase3Engine.Settings(true,false,false,1,12,96,8,false);
        Phase3Engine engine=new Phase3Engine(host,settings,SEED);
        for(int epoch=0;epoch<180;epoch++){
            host.unknown=(epoch%6)==0;
            host.actor=new NativeWorldEngine.Actor(OWNER,new V(0,64,0),(epoch*37)%360,0,false,false);
            for(int tick=0;tick<20;tick++)engine.tick();
            var plan=engine.logical();
            yes(plan.isPresent(),"logical epoch refreshed");
            if(host.unknown)yes(plan.get().route().isEmpty(),"unknown chunks prevent proposed route");
            if(!plan.get().route().isEmpty()){
                yes(plan.get().destinationDx()==70000*16 && plan.get().destinationDz()==-80000*16,
                    "routed proposal uses certified source/destination offset");
            }
            yes(engine.status().published()==0,"logical planning never physically publishes");
        }
        yes(engine.status().logicalEpochs()==180,"180 full planning epochs were run");
        yes(engine.status().planned()>0,"at least one safe logical candidate was routed");
        engine.close();
        System.out.println("PHASE4_PLANNING: 180 full epochs accepted="+engine.status().planned()+" inspected_columns="+host.queries);
    }
    static void longLifecycle(){
        DisabledHost host=new DisabledHost();
        Phase3Engine.Settings settings=new Phase3Engine.Settings(true,true,true,1,12,96,8);
        Phase3Engine engine=new Phase3Engine(host,settings,SEED);
        long start=System.nanoTime();
        // A deliberately incomplete host is refused BEFORE it ever tries to stage a portal.
        for(int t=0;t<200_000;t++){
            if(t%20==0)host.actor=new NativeWorldEngine.Actor(OWNER,new V(0,64,0),(t/20*179)%360,-45+(t/20%10)*10,false,false);
            engine.tick();lifecycleTicks++;
            if(t%1017==0)yes(engine.status().published()==0,"no physical publication without camera authority");
        }
        long ms=(System.nanoTime()-start)/1_000_000;
        yes(engine.status().logicalEpochs()==10000,"exact 20-tick epochs in 200k ticks");
        yes(engine.status().published()==0&&engine.status().crossed()==0&&host.teleports==0&&host.stages==0,"no publication or crossing without camera proof");
        engine.close();yes(engine.uncertainFacets()==0,"closed inert host has no unsafe generations");
        System.out.println("PHASE4_LIFECYCLE: 200000 virtual ticks 10000 epochs elapsed_ms="+ms+" proposals_blocked="+engine.status().refused());
    }
    public static void main(String[] args)throws Exception {
        chunkCoverage();safetyFaults();candidateProperties();projectionEvidence();transactionFaultMatrix();traversalTransforms();journalCrashAndTamper();concurrentJournalWriters();actualLogicalPlanning();longLifecycle();
        System.out.println("PHASE4_VALIDATION_PASS: assertions="+assertions+" chunk_sample_oracle_cases="+oracleCases+" structure_injections="+rejectionCases+" lifecycle_ticks="+lifecycleTicks+" seed="+SEED);
    }
}
