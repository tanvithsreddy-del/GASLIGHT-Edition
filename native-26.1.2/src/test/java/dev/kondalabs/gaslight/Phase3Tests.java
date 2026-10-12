package dev.kondalabs.gaslight;

import java.util.*;
import dev.kondalabs.gaslight.Geometry.*;
import dev.kondalabs.gaslight.NativePortal.*;

/** Self-contained Phase 3 tests: no Fabric mocks or simulated green Minecraft launch. */
public final class Phase3Tests {
    private static long count=0;
    private static void ok(boolean value,String label){count++;if(!value)throw new AssertionError(label+" at assertion "+count);}
    private static void close(double a,double b,double eps,String label){ok(Math.abs(a-b)<eps,label+" "+a+" != "+b);}
    private static Geometry.Camera camera(double yaw){return Geometry.Camera.fromYawPitch(0,65.62,0,yaw,0,90,16.0/9.0,128);}

    private static void optics(){
        Geometry.Camera cam=camera(0);
        ok(Phase3Visibility.hidden(cam,new Surface(new V(0,66,-25),new V(1,0,0),10,24),3),"rear hidden");
        ok(!Phase3Visibility.hidden(cam,new Surface(new V(0,66,25),new V(1,0,0),10,24),3),"front visible");
        ok(!Phase3Visibility.hidden(cam,new Surface(new V(0,66,-1),new V(1,0,0),10,24),3),"partially visible near plane");
        for(int f:new int[]{8,12,16,24,32,64}){
            List<V2> loop=Phase3Visibility.conservativeLoop(cam,f,12,64);
            ok(loop.size()==f,"loop vertices");
            ok(Geometry.polygonEncloses(loop,new V2(0,0)),"loop encloses player");
            double rad=0;for(V2 corner:cam.projectedCorners())rad=Math.max(rad,corner.norm());
            for(Segment s:Geometry.segments(loop)){
                ok(Geometry.pointSegmentDistance(new V2(0,0),s)>rad+11.9,"whole chord excludes projected frustum");
            }
        }
        Random rng=new Random(45622);
        for(int i=0;i<2400;i++){
            double yaw=-180+rng.nextDouble()*360,pitch=-75+rng.nextDouble()*150;
            Geometry.Camera view=Geometry.Camera.fromYawPitch(rng.nextDouble()*200-100,64,rng.nextDouble()*200-100,
                yaw,pitch,65+rng.nextDouble()*45,1.1+rng.nextDouble()*1.2,48+rng.nextDouble()*120);
            V2 pos=new V2(view.eye().x(),view.eye().z());
            List<V2> loop=Phase3Visibility.conservativeLoop(view,16,8,70);
            double max=0;for(V2 corner:view.projectedCorners())max=Math.max(max,corner.minus(pos).norm());
            for(Segment s:Geometry.segments(loop)){
                ok(Geometry.pointSegmentDistance(pos,s)>max+7.9,"stochastic loop chord outside view");
            }
        }
    }

    private static void routes(){
        Phase3Route.Limits lim=new Phase3Route.Limits(1,4000,200,280,40);
        Phase3Route.Plan clear=Phase3Route.route(new V2(8,8),new V2(8+16*7,8),80000,-92000,
            (x,z)->StructureEnvelope.Result.SAFE,lim);
        ok(clear.accepted(),"clear route accepted "+clear.reason());
        ok(!clear.segments().isEmpty(),"has facets");
        ok(clear.inspections()<=lim.maxQueries(),"query ceiling");
        Phase3Route.Plan blocked=Phase3Route.route(new V2(8,8),new V2(8+16*7,8),80000,-92000,
            (x,z)->(x==3&&z==0)?StructureEnvelope.Result.STRUCTURE:StructureEnvelope.Result.SAFE,lim);
        ok(blocked.accepted(),"bounded route avoids registered structure: "+blocked.reason());
        for(Segment s:blocked.segments()){
            Set<Chunk> checked=Geometry.chunksTouched(s,16.5,280);
            ok(!checked.contains(new Chunk(3,0)),"no physical seam through protected source column");
        }
        // Identical structure veto on the destination side.
        Phase3Route.Plan destBlocked=Phase3Route.route(new V2(8,8),new V2(8+16*7,8),80000,-92000,
            (x,z)->(x==80003&&z==-92000)?StructureEnvelope.Result.STRUCTURE:StructureEnvelope.Result.SAFE,lim);
        ok(destBlocked.accepted(),"route avoids protected destination: "+destBlocked.reason());
        for(Segment s:destBlocked.segments())for(Chunk source:Geometry.chunksTouched(s,16.5,280))
            ok(!(source.x()+80000==80003&&source.z()-92000==-92000),"no physical seam through protected destination column");
        Phase3Route.Plan unknown=Phase3Route.route(new V2(8,8),new V2(120,8),80000,-92000,
            (x,z)->StructureEnvelope.Result.UNKNOWN,lim);
        ok(!unknown.accepted(),"unknown fails closed");
        Phase3Route.Plan tooSmall=Phase3Route.route(new V2(8,8),new V2(120,8),80000,-92000,
            (x,z)->StructureEnvelope.Result.SAFE,new Phase3Route.Limits(1,5,10,16,4));
        ok(!tooSmall.accepted(),"inspection budget fails closed");
        Phase3Route.Plan playerProtected=Phase3Route.route(new V2(8,8),new V2(120,8),80000,-92000,
            (x,z)->x==0&&z==0?StructureEnvelope.Result.USER_PROTECTED:StructureEnvelope.Result.SAFE,lim);
        ok(!playerProtected.accepted(),"user protected fails closed");
        Phase3Route.Plan border=Phase3Route.route(new V2(8,8),new V2(120,8),1_900_000,0,
            (x,z)->StructureEnvelope.Result.SAFE,lim);
        ok(!border.accepted(),"world border fails closed");
        // Partial-trust surveys must refuse, never treat missing return values as SAFE.
        Phase3Route.Plan nullOracle=Phase3Route.route(new V2(8,8),new V2(120,8),80000,-92000,
            (x,z)->null,lim);
        ok(!nullOracle.accepted(),"null inspector fails closed");
        Phase3Route.Plan brokenOracle=Phase3Route.route(new V2(8,8),new V2(120,8),80000,-92000,
            (x,z)->{throw new IllegalStateException("forced failure");},lim);
        ok(!brokenOracle.accepted(),"thrown inspector fails closed");
        Random rng=new Random(123);
        for(int k=0;k<80;k++){
            int ox=74000+rng.nextInt(10000),oz=-74000-rng.nextInt(10000);
            Phase3Route.Plan path=Phase3Route.route(new V2(8,8),new V2(152,8),ox,oz,
                (x,z)->StructureEnvelope.Result.SAFE,lim);
            ok(path.accepted(),"many paired translations are geometrically certified");
            for(Segment seg:path.segments()){
                ok(seg.length()<=128.0000001,"facet width bound");
                ok(seg.length()>0,"facet positive width");
            }
        }
    }

    private static final class Host implements Phase3Engine.Host {
        private final Phase3RecoveryJournal.TestMemoryStore journal=new Phase3RecoveryJournal.TestMemoryStore();
        @Override public Optional<Phase3RecoveryJournal.Store> recoveryJournal(){return Optional.of(journal);}

        final UUID id=UUID.fromString("00000000-0000-0000-0000-000000000317");
        NativeWorldEngine.Actor actor=new NativeWorldEngine.Actor(id,new V(0,64,0),0,0,false,false);
        boolean trustworthy=true,structure=false,multiplayer=false,throwOnPublish=false,publishAllowed=true;
        int adds=0,removes=0,teleports=0;
        double score=.95;
        @Override public Collection<NativeWorldEngine.Actor> players(){return List.of(actor);}
        @Override public boolean singleplayer(){return !multiplayer;}
        @Override public boolean loadChunk(int x,int z){return true;}
        @Override public StructureEnvelope.Result inspectChunk(int x,int z){
            return structure?StructureEnvelope.Result.STRUCTURE:StructureEnvelope.Result.SAFE;
        }
        @Override public boolean canExit(NativeWorldEngine.Actor a,V p){return !structure;}
        @Override public boolean teleport(NativeWorldEngine.Actor a,V p,double y){teleports++;return !structure;}
        @Override public Optional<Geometry.Camera> camera(NativeWorldEngine.Actor p){return Optional.of(Phase3Tests.camera(p.yaw()));}
        @Override public boolean authoritativeCamera(NativeWorldEngine.Actor p){return trustworthy;}
        @Override public Optional<Phase3Engine.VerticalSpan> verticalSpan(){
            return Optional.of(new Phase3Engine.VerticalSpan(-64,320));
        }
        @Override public Optional<Phase3CameraWitness.Witness> cameraWitness(
                NativeWorldEngine.Actor p,long tick){
            if(!trustworthy)return Optional.empty();
            return Optional.of(new Phase3CameraWitness.Witness(id,tick,tick,tick,
                Phase3Tests.camera(p.yaw()),true,true));
        }
        @Override public Optional<Phase3Publication.Transaction> stagePublication(List<Passage> facets){
            if(throwOnPublish||!publishAllowed)return Optional.empty();
            return Optional.of(new Phase3Publication.Transaction(){
                @Override public boolean staged(){return true;}
                @Override public boolean commit(){adds+=facets.size();return true;}
                @Override public boolean abort(){return true;}
            });
        }
        @Override public double terrainScore(Chunk a,Chunk b){return score;}
        @Override public boolean retireAtomically(List<Passage> facets){
            removes+=facets.size();return true;
        }
    }
    private static void loopRoutes(){
        List<V2> polygon=new ArrayList<>();
        for(int i=0;i<8;i++){
            double ang=i*Math.PI/4;
            polygon.add(new V2(8+100*Math.cos(ang),8+100*Math.sin(ang)));
        }
        Phase3Route.Limits bounds=new Phase3Route.Limits(1,1200,120,200,24);
        Phase3LoopRoute.Result done=Phase3LoopRoute.route(polygon,80000,-90000,
            (x,z)->StructureEnvelope.Result.SAFE,bounds,128,9600);
        ok(done.accepted(),"bounded closed loop certified: "+done.reason());
        ok(done.segments().size()>=8,"finite loop has facets");
        ok(!done.checkedSource().isEmpty()&&!done.checkedDestination().isEmpty(),"both sides surveyed");
        for(int i=0;i<done.segments().size();i++){
            Segment a=done.segments().get(i),b=done.segments().get((i+1)%done.segments().size());
            ok(a.b().minus(b.a()).norm()<1e-6,"loop closes without geometry gaps");
        }
        var unknown=Phase3LoopRoute.route(polygon,80000,-90000,
            (x,z)->StructureEnvelope.Result.UNKNOWN,bounds,128,9600);
        ok(!unknown.accepted()&&unknown.segments().isEmpty(),"unknown rejects entire loop");
        var cap=Phase3LoopRoute.route(polygon,80000,-90000,
            (x,z)->StructureEnvelope.Result.SAFE,bounds,7,9600);
        ok(!cap.accepted()&&cap.segments().isEmpty(),"facet cap rejects entire loop");
        var obstacle=Phase3LoopRoute.route(polygon,80000,-90000,
            (x,z)->(x>79990&&x<80020&&z>-90020&&z< -89980)
                ?StructureEnvelope.Result.STRUCTURE:StructureEnvelope.Result.SAFE,bounds,128,9600);
        ok(!obstacle.accepted()||obstacle.segments().stream().allMatch(seg->
            Geometry.chunksTouched(seg,16.5,200).stream().noneMatch(c->
                c.x()+80000>79990&&c.x()+80000<80020&&c.z()-90000> -90020&&c.z()-90000< -89980)),
            "destination obstacles cannot be traversed by accepted loops");
    }
    private static void protections(){
        var valid=ProtectedChunkRegions.parse(List.of("# reserved regions", "-7, 3, 12, 20", "0,0,0,0"));
        ok(valid.valid(),"valid protection policy parses");
        ok(valid.protectedAt(4,9),"all chunk columns inside region protected");
        ok(valid.protectedAt(0,0),"single-chunk region protected");
        ok(!valid.protectedAt(14,9),"unrelated chunk not protected");
        for(String malformed:List.of("0,1,2", "0,0,-1,0", "nan,2,3,4", "0,0,2000000,1", "2,0,1,2", "0,0,0,0,0")){
            var invalid=ProtectedChunkRegions.parse(List.of(malformed));
            ok(!invalid.valid(),"malformed policy rejected: "+malformed);
            ok(invalid.protectedAt(0,0),"invalid protection policy blocks all");
        }
        var missing=ProtectedChunkRegions.parse(List.of());
        ok(missing.valid()&&!missing.protectedAt(0,0),"empty policy leaves unmarked chunks to structure metadata");
    }
    private static void engine(){
        Host safe=new Host();
        Phase3Engine.Settings cfg=new Phase3Engine.Settings(true,true,false,1,16,128,8);
        Phase3Engine e=new Phase3Engine(safe,cfg,777);
        for(int i=0;i<20;i++)e.tick();
        ok(e.status().logicalEpochs()==1,"20-tick epoch generated");
        ok(e.status().ringVertices()==16,"closed analytic loop ready");
        ok(e.status().planned()==1,"loaded safe paired route planned: "+e.status().recentStatus());
        ok(e.status().published()==1,"hidden facets published in authoritative test host: "+e.status().recentStatus());
        ok(e.status().activeFacets()>0,"has published facets");
        ok(safe.adds==e.status().activeFacets(),"every published facet has callback");
        // A structure becomes known after activation. Retire the entire generation off-screen.
        safe.structure=true;
        for(int i=0;i<20;i++)e.tick();
        ok(e.status().logicalEpochs()==2,"second 20-tick epoch");
        ok(e.status().activeFacets()==0,"unsafe generation removed");
        ok(safe.removes==safe.adds,"all facets received retirement callback");
        e.close();
        Host untrusted=new Host();untrusted.trustworthy=false;
        Phase3Engine gated=new Phase3Engine(untrusted,cfg,777);
        for(int i=0;i<20;i++)gated.tick();
        ok(gated.status().logicalEpochs()==1,"untrusted camera still generates logical epochs");
        ok(gated.status().published()==0&&untrusted.adds==0,"untrusted camera cannot publish");
        ok(gated.status().ringVertices()==16,"untrusted camera has logical closed curve");
        Host dry=new Host();
        Phase3Engine noPhysical=new Phase3Engine(dry,
            new Phase3Engine.Settings(true,false,false,1,16,128,8),777);
        for(int i=0;i<60;i++)noPhysical.tick();
        ok(noPhysical.status().logicalEpochs()==3,"three fixed 20-tick epochs");
        ok(noPhysical.status().planned()==3,"three independent route proposals");
        ok(noPhysical.status().published()==0&&dry.adds==0,"no physical publication in logical-only mode");
        Host closedHost=new Host();
        Phase3Engine closed=new Phase3Engine(closedHost,
            new Phase3Engine.Settings(true,false,false,1,16,128,8,true),777);
        for(int i=0;i<20;i++)closed.tick();
        ok(closed.status().logicalEpochs()==1,"closed-loop engine epoch");
        ok(closed.status().planned()==1,"entire finite closed loop planned in mock world: "+closed.status().recentStatus());
        ok(closed.logical().orElseThrow().route().size()>=16,"entire loop has physical candidate facets");
        List<Segment> loopRoute=closed.logical().orElseThrow().route();
        for(int i=0;i<loopRoute.size();i++)
            ok(loopRoute.get(i).b().minus(loopRoute.get((i+1)%loopRoute.size()).a()).norm()<1e-6,
               "scheduler loop ends meet");
        ok(closed.status().published()==0,"whole loop remains unpublished when physical=false");
        closed.close();
        Host weak=new Host();weak.score=.1;
        Phase3Engine mismatch=new Phase3Engine(weak,cfg,777);
        for(int i=0;i<20;i++)mismatch.tick();
        ok(mismatch.status().published()==0,"bad terrain similarity rejected");
        Host veto=new Host();veto.structure=true;
        Phase3Engine blocked=new Phase3Engine(veto,cfg,777);
        for(int i=0;i<20;i++)blocked.tick();
        ok(blocked.status().published()==0,"protected world rejected");
        Host multi=new Host();multi.multiplayer=true;
        Phase3Engine multiplayer=new Phase3Engine(multi,cfg,777);
        for(int i=0;i<20;i++)multiplayer.tick();
        ok(multiplayer.status().published()==0,"multiplayer fail closed");
        Host noRenderer=new Host();noRenderer.publishAllowed=false;
        Phase3Engine lacksRenderer=new Phase3Engine(noRenderer,cfg,777);
        for(int i=0;i<20;i++)lacksRenderer.tick();
        ok(lacksRenderer.status().published()==0&&noRenderer.adds==0,
            "renderer-less host cannot publish physical facets");
        Host steady=new Host();
        Phase3Engine stress=new Phase3Engine(steady,cfg,778);
        for(int i=0;i<500;i++){
            stress.tick();
            ok(stress.status().activeFacets()<=32,"bounded active facets across many epochs");
        }
        ok(stress.status().logicalEpochs()==25,"logical scheduler continues under capacity pressure");
        stress.close();
        ok(steady.adds==steady.removes,"shutdown retires every published facet in mock host");
        Host failed=new Host();failed.throwOnPublish=true;
        Phase3Engine rollback=new Phase3Engine(failed,cfg,777);
        for(int i=0;i<20;i++)rollback.tick();
        ok(rollback.status().published()==0,"failed publication not recorded active");
        ok(failed.adds==failed.removes,"failed atomic publisher left no partial link");
        e.close();gated.close();noPhysical.close();mismatch.close();blocked.close();multiplayer.close();rollback.close();lacksRenderer.close();
    }
    public static void main(String[] args){
        optics();routes();loopRoutes();protections();engine();
        System.out.println("Phase3 optics/routes passed "+count);
    }
}
