package dev.kondalabs.gaslight;

import java.util.*;
import dev.kondalabs.gaslight.Geometry.*;
import dev.kondalabs.gaslight.NativePortal.*;

/** Phase 3: bounded, server-side, deterministic automatic curve planner.
 * 20-tick epochs always advance the logical plan when enabled. Physical facets
 * are OPTIONAL and fail closed until a trustworthy live client camera exists.
 * This is neither a world renderer nor a fully seamless portal system.
 */
public final class Phase3Engine {
    /** Vertical extent of the authoritative world; exclusive upper bound. */
    public record VerticalSpan(int minY,int maxYExclusive) {
        public VerticalSpan {
            if(maxYExclusive<=minY||maxYExclusive-minY>1000)
                throw new IllegalArgumentException("invalid world span");
        }
        public double midpoint(){return minY+(maxYExclusive-minY)*.5;}
        public double height(){return maxYExclusive-minY;}
    }
    public interface Host extends NativeWorldEngine.Host {
        default Optional<Geometry.Camera> camera(NativeWorldEngine.Actor player){return Optional.empty();}
        /** NEVER true for the normal server player yaw/pitch approximation. */
        default boolean authoritativeCamera(NativeWorldEngine.Actor player){return false;}
        /** Negative/nonfinite scores reject a candidate. Assumes *already loaded* terrain. */
        default double terrainScore(Geometry.Chunk source,Geometry.Chunk destination){return Double.NaN;}
        /** Nine-point biome/material/fluid/height fingerprint. Empty means UNKNOWN. */
        default Optional<Phase3DestinationProfile.Profile> terrainProfile(Geometry.Chunk chunk){return Optional.empty();}
        /** Real worlds must use full fingerprints. Legacy synthetic Host tests may
         * exercise the previous numeric sampler without claiming production safety. */
        default boolean requireDetailedTerrainProfiles(){return false;}
        /** Optional bounded, previously known-and-loaded remote areas, as chunk offsets.
         * No implicit chunk generation and no promise that any candidate is safe.
         */
        default List<Geometry.Chunk> preparedDestinationOffsets(NativeWorldEngine.Actor player){return List.of();}
        /** The real Minecraft adapter does not implement atomic GPU/world publication.
         * Its default MUST remain false until an integrated renderer can stage every facet.
         */
        /** Atomic staged publisher. The actual Fabric host intentionally supplies none. */
        default Optional<Phase3Publication.Transaction> stagePublication(List<Passage> facets){return Optional.empty();}
        /** The actual Fabric host intentionally supplies no authenticated client view. */
        /** Require actual world height instead of a 32-block-high make-believe seam. */
        default Optional<VerticalSpan> verticalSpan(){return Optional.empty();}
        default Optional<Phase3CameraWitness.Witness> cameraWitness(
                NativeWorldEngine.Actor actor,long serverTick){return Optional.empty();}
        /** Caller must avoid exposing partially retired links. */
        default boolean retireAtomically(List<Passage> facets){return false;}
        /** Durable fail-closed intent ledger required for physical publication. */
        default Optional<Phase3RecoveryJournal.Store> recoveryJournal(){return Optional.empty();}
    }
    public record Settings(boolean enabled,boolean physical,boolean crossing,int candidates,int facets,
                           double farDistance,double safetyMargin,boolean closedLoopMode){
        public Settings(boolean enabled,boolean physical,boolean crossing,int candidates,int facets,
                        double farDistance,double safetyMargin){
            this(enabled,physical,crossing,candidates,facets,farDistance,safetyMargin,false);
        }
        public Settings {
            if(candidates<1||candidates>8||facets<8||facets>64||!Double.isFinite(farDistance)||
                farDistance<16||farDistance>256||!Double.isFinite(safetyMargin)||safetyMargin<2)
                throw new IllegalArgumentException("settings");
        }
    }
    public record Status(long ticks,long logicalEpochs,long planned,long published,long crossed,long refused,
                         int activeFacets,int ringVertices,String recentStatus){}
    public record Logical(long epoch,List<V2> loop,Geometry.InfiniteRearLine rear,
                          List<Segment> route, int destinationDx,int destinationDz){}
    private record Published(long epoch,long startTick,List<Passage> facets){}
    private final Host host;
    private final Phase3RecoveryJournal.Store journal;
    private final Settings cfg;
    private final long seed;
    private final Phase3Route.Limits limits=new Phase3Route.Limits(1,480,48,128,16);
    private final PortalCrossingController walker=new PortalCrossingController(16);
    private final ArrayDeque<Published> active=new ArrayDeque<>();
    private final ArrayDeque<Published> uncertain=new ArrayDeque<>();
    private UUID owner;
    private long ticks=0,epochs=0,planned=0,published=0,crossed=0,refused=0;
    private String recent="not started";
    private Logical logical;
    private boolean disabled=false;
    private long lastCameraSequence=-1;
    public Phase3Engine(Host host,Settings cfg,long worldSeed){
        this.host=Objects.requireNonNull(host);this.cfg=Objects.requireNonNull(cfg);this.seed=worldSeed;
        // A broken Host must never escape construction and encourage a caller to
        // silently re-create a controller with a fresh, empty publication ledger.
        Phase3RecoveryJournal.Store resolved=null;
        boolean journalFailed=false;
        try {
            Optional<Phase3RecoveryJournal.Store> response=host.recoveryJournal();
            if(response==null)journalFailed=true;
            else resolved=response.orElse(null);
        }catch(RuntimeException failure){journalFailed=true;}
        this.journal=resolved;
        boolean openedDirty=journalFailed || resolved==null;
        if(resolved!=null) {
            try {openedDirty=resolved.blockedOnOpen();}
            catch(RuntimeException failure){openedDirty=true;}
        }
        if(cfg.physical() && openedDirty){
            disabled=true;recent="QUARANTINED: missing, dirty or unavailable recovery journal";
        }
    }
    public Status status(){
        int count=active.stream().mapToInt(p->p.facets.size()).sum()
            +uncertain.stream().mapToInt(p->p.facets.size()).sum();
        return new Status(ticks,epochs,planned,published,crossed,refused,count,
            logical==null?0:logical.loop.size(),recent);
    }
    public Optional<Logical> logical(){return Optional.ofNullable(logical);}
    /** Stop all planning and crossing immediately. Never forget an unretired bundle. */
    public void close(){
        disabled=true;
        if(owner!=null)walker.forget(owner);
        logical=null;
        retryRetireQuarantined();
    }
    /** Best-effort retry; outstanding generations remain explicitly tracked on failure. */
    public boolean retryRetireQuarantined(){
        retireRecords(active);
        retireRecords(uncertain);
        recent=active.isEmpty()&&uncertain.isEmpty() ?
            "closed; all retired":"QUARANTINED: retirement not acknowledged";
        return active.isEmpty()&&uncertain.isEmpty();
    }
    public int uncertainFacets(){return uncertain.stream().mapToInt(p->p.facets.size()).sum();}
    private void retireRecords(ArrayDeque<Published> records){
        for(Iterator<Published> it=records.iterator();it.hasNext();){
            Published publication=it.next();
            // This retry may run from close() or a recovery command. Never tear
            // down a seam underneath a player, even during an explicit retry.
            if(!safeToRetire(publication.facets))continue;
            boolean retired=false;
            try{retired=host.retireAtomically(publication.facets);}catch(RuntimeException ignored){}
            if(retired && confirmJournalRetirement(publication))it.remove();
            else if(retired)disabled=true;
        }
    }
    private boolean safeToRetire(List<Passage> facets){
        try {
            Collection<NativeWorldEngine.Actor> actors=host.players();
            if(actors==null)return false;
            for(NativeWorldEngine.Actor actor:actors)
                if(actor==null||near(actor.position(),facets,10))return false;
            return true;
        }catch(RuntimeException exception){return false;}
    }
    private boolean confirmJournalRetirement(Published p){
        if(journal==null)return false;
        try {return journal.settle(p.facets.get(0).owner(),p.epoch);}
        catch(RuntimeException failure){
            disabled=true;
            recent="QUARANTINED: recovery journal settlement failed";
            return false;
        }
    }
    private void refuse(String message){refused++;recent=message;}
    public void tick(){
        ticks++;
        if(!cfg.enabled||disabled)return;
        try {tickGuarded();}
        catch(RuntimeException unexpected){
            // Camera, terrain, structure and journal adapters are external code.
            // Any unchecked failure invalidates the authority of this cycle.
            disabled=true;
            if(owner!=null)walker.forget(owner);
            refuse("QUARANTINED: untrusted host operation failed: "+unexpected.getClass().getSimpleName());
        }
    }
    private void tickGuarded(){
        boolean localSession;
        Collection<NativeWorldEngine.Actor> people;
        try {
            localSession=host.singleplayer();
            people=host.players();
        }catch(RuntimeException e){
            disabled=true;
            if(owner!=null)walker.forget(owner);
            refuse("QUARANTINED: world/player host inspection failed");
            return;
        }
        if(!localSession){close();recent="singleplayer only";return;}
        if(people==null){
            disabled=true;
            if(owner!=null)walker.forget(owner);
            refuse("QUARANTINED: unavailable player snapshot");
            return;
        }
        if(people.size()!=1){if(!active.isEmpty()||!uncertain.isEmpty())close();return;}
        NativeWorldEngine.Actor actor=people.iterator().next();
        if(actor==null||actor.id()==null||actor.position()==null||
           !Double.isFinite(actor.position().x())||
           !Double.isFinite(actor.position().y())||
           !Double.isFinite(actor.position().z())||
           !Double.isFinite(actor.yaw())||!Double.isFinite(actor.pitch())){
            disabled=true;
            if(owner!=null)walker.forget(owner);
            refuse("QUARANTINED: invalid player state");
            return;
        }
        if(owner!=null&&!owner.equals(actor.id())){close();recent="owner changed";return;}
        owner=actor.id();
        if(actor.spectator()||actor.passenger()){
            walker.forget(actor.id());if(ticks%20==0)refuse("spectator/passenger");return;
        }
        if(ticks%20==0){
            epochs++;
            // Existing physical generations MUST be certified before proposing or
            // committing replacements. A newly published generation must never
            // hide a safety failure in the older one.
            for(Iterator<Published> it=active.iterator();it.hasNext();){
                Published p=it.next();
                if(!certify(p.facets)){
                    walker.forget(actor.id());
                    if(near(actor.position(),p.facets,10)){
                        // Do not retire under the player and NEVER allow traversal
                        // through an uncertified seam on this or subsequent ticks.
                        disabled=true;
                        refuse("QUARANTINED: active generation unsafe near actor; crossing disabled");
                    }else{
                        boolean retired=false;
                        try{retired=host.retireAtomically(p.facets);}catch(RuntimeException ignored){}
                        if(retired && confirmJournalRetirement(p)){
                            it.remove();refuse("active pair invalidated and retired");
                        }else{
                            disabled=true;
                            refuse("QUARANTINED: retirement/journal reconciliation failed");
                        }
                    }
                }
            }
            if(disabled)return;
            prepare(actor);
        }
        // Retire only when a NEW, non-replayed synchronized client camera
        // certifies BOTH sides hidden, not just the source side. In particular,
        // after teleporting far away the exit may be plainly visible.
        Optional<Geometry.Camera> retirementView=Optional.empty();
        if(!active.isEmpty()&&host.authoritativeCamera(actor)){
            Phase3CameraWitness.Witness w=host.cameraWitness(actor,ticks).orElse(null);
            var assessment=Phase3CameraWitness.assess(w,actor.id(),ticks,lastCameraSequence,
                new Geometry.V3(actor.position().x(),actor.position().y()+1.62,actor.position().z()),2,.5);
            if(assessment.accepted()){
                lastCameraSequence=w.sequence();retirementView=Optional.of(w.camera());
            }
        }
        for(Iterator<Published> it=active.iterator();it.hasNext();){
            Published p=it.next();
            if(ticks-p.startTick<100||!safeToRetire(p.facets)||retirementView.isEmpty())continue;
            Geometry.Camera camera=retirementView.get();
            if(p.facets.stream().allMatch(q->
                Phase3Visibility.hidden(camera,q.entrance(),cfg.safetyMargin())&&
                Phase3Visibility.hidden(camera,q.exit(),cfg.safetyMargin()))){
                boolean retired=false;
                try{retired=host.retireAtomically(p.facets);}catch(RuntimeException ignored){}
                if(retired && confirmJournalRetirement(p))it.remove();
                else {disabled=true;walker.forget(actor.id());refuse("QUARANTINED: retirement/journal failed");}
            }
        }
        if(disabled||!cfg.crossing||!cfg.physical||active.isEmpty())return;
        // Never permit crossing while the live camera contract has disappeared.
        // This guard cannot substitute for a Minecraft 3D compositor/frame fence.
        if(!host.authoritativeCamera(actor)){
            walker.forget(actor.id());refuse("crossing refused: camera authority lost");return;
        }
        List<Passage> crossings=new ArrayList<>();
        for(Published publication:active)crossings.addAll(publication.facets);
        Optional<PortalCrossingController.Hop> hop=walker.visit(actor.id(),actor.position(),actor.yaw(),ticks,crossings);
        if(hop.isPresent()){
            PortalCrossingController.Hop h=hop.get();
            if(!certify(List.of(h.portal()))||!host.canExit(actor,h.crossing().exitPoint())||
                !host.teleport(actor,h.crossing().exitPoint(),h.exitYaw())){
                walker.forget(actor.id());refuse("unsafe crossing refused");
            }else crossed++;
        }
    }
    private Phase3TerrainMatcher.Verdict terrainAudit(List<Segment> route,int dx,int dz,
             Map<Chunk,Optional<Phase3DestinationProfile.Profile>> cache){
        if(!host.requireDetailedTerrainProfiles())
            return Phase3TerrainMatcher.evaluate(route,dx,dz,host::terrainScore,
                .7,.8,Math.max(64,cfg.facets()*20));
        return Phase3TerrainMatcher.evaluate(route,dx,dz,(a,b)->{
            Optional<Phase3DestinationProfile.Profile> ap=budgetedProfile(cache,a);
            Optional<Phase3DestinationProfile.Profile> bp=budgetedProfile(cache,b);
            if(ap==null||bp==null)throw new IllegalArgumentException("null terrain profile");
            if(ap.isPresent()&&bp.isPresent()){
                Phase3DestinationProfile.Match match=Phase3DestinationProfile.compare(ap.get(),bp.get());
                return match.accepted()?match.score():Double.NaN;
            }
            if(host.requireDetailedTerrainProfiles())return Double.NaN;
            // Compatibility only for offline legacy tests, not the actual Minecraft host.
            return host.terrainScore(a,b);
        },.7,.8,Math.max(64,cfg.facets()*20));
    }
    private Optional<Phase3DestinationProfile.Profile> budgetedProfile(
          Map<Chunk,Optional<Phase3DestinationProfile.Profile>> cache,Chunk chunk){
        if(!cache.containsKey(chunk) && cache.size()>=128)
            throw new IllegalStateException("profile read budget exceeded");
        return cache.computeIfAbsent(chunk,host::terrainProfile);
    }
    private boolean certify(List<Passage> facets){
        return Phase3SafetyGate.check(facets,1,4000,host::inspectChunk).accepted();
    }
    private static boolean near(V player,List<Passage> facets,double distance){
        for(Passage p:facets)for(Surface s:List.of(p.entrance(),p.exit())){
            if(Math.abs(s.side(player))<=distance && Math.abs(s.across(player))<=s.width()/2+distance &&
               Math.abs(s.altitude(player))<=s.height()/2+distance)return true;
        }
        return false;
    }
    private void prepare(NativeWorldEngine.Actor actor){
        Geometry.Camera cam=host.camera(actor).orElseGet(()->
            Geometry.Camera.fromYawPitch(actor.position().x(),actor.position().y()+1.62,
                actor.position().z(),actor.yaw(),actor.pitch(),110,16.0/9.0,cfg.farDistance()));
        try{
            List<V2> loop=Phase3Visibility.conservativeLoop(cam,cfg.facets(),cfg.safetyMargin(),64);
            // Closed loop is a mathematical target; it is NOT silently materialized.
            V2 center=new V2(cam.eye().x(),cam.eye().z());
            V2 h=new V2(-Math.sin(Math.toRadians(actor.yaw())),Math.cos(Math.toRadians(actor.yaw())));
            Geometry.InfiniteRearLine rear=Geometry.rear(center,h,cam.projectedCorners(),40,cfg.safetyMargin());
            logical=new Logical(epochs,loop,rear,List.of(),0,0);
            Phase3CameraWitness.Witness witness=null;
            if(cfg.physical){
                if(!host.authoritativeCamera(actor)){
                    refuse("physical publication withheld: no authoritative client view");return;
                }
                witness=host.cameraWitness(actor,ticks).orElse(null);
                var assessment=Phase3CameraWitness.assess(witness,actor.id(),ticks,lastCameraSequence,
                    new Geometry.V3(actor.position().x(),actor.position().y()+1.62,actor.position().z()),2,0.5);
                if(!assessment.accepted()){
                    refuse("physical publication withheld: "+assessment.reason());return;
                }
                lastCameraSequence=witness.sequence();
                cam=witness.camera();
                // Physical plans must derive every curve from the attested camera.
                loop=Phase3Visibility.conservativeLoop(cam,cfg.facets(),cfg.safetyMargin(),64);
                center=new V2(cam.eye().x(),cam.eye().z());
                V2 cameraFacing=new V2(cam.forward().x(),cam.forward().z());
                if(cameraFacing.norm()>1e-6)h=cameraFacing.unit();
                rear=Geometry.rear(center,h,cam.projectedCorners(),40,cfg.safetyMargin());
                logical=new Logical(epochs,loop,rear,List.of(),0,0);
            }
            // Every 20 ticks refresh mathematical state. Only a bounded subset of it may become real.
            V2 start=rear.at(-24),end=rear.at(24);
            List<Segment> best=null;int bestDx=0,bestDz=0;
            double bestScore=Double.NEGATIVE_INFINITY;
            // Destinations are deterministic, deduplicated and bounded. A catalog
            // entry still requires all per-chunk validation before it can win.
            List<Phase3DestinationSelector.Candidate> options=
                Phase3DestinationSelector.candidates(host.preparedDestinationOffsets(actor),
                    cfg.candidates(),seed,epochs,start,end);
            Map<Chunk,Optional<Phase3DestinationProfile.Profile>> candidateProfiles=new HashMap<>();
            for(Phase3DestinationSelector.Candidate option:options){
                int offCx=option.offset().x(),offCz=option.offset().z();
                List<Segment> proposed;
                if(cfg.closedLoopMode()){
                    Phase3LoopRoute.Result entire=Phase3LoopRoute.route(loop,offCx,offCz,
                        host::inspectChunk,limits,Math.min(64,cfg.facets()*4),cfg.facets()*480);
                    if(!entire.accepted())continue;
                    proposed=entire.segments();
                }else{
                    Phase3Route.Plan route=Phase3Route.route(start,end,offCx,offCz,host::inspectChunk,limits);
                    if(!route.accepted())continue;
                    proposed=route.segments();
                }
                // Every physical facet is sampled, not just a lucky three points.
                var match=terrainAudit(proposed,offCx,offCz,candidateProfiles);
                if(match.accepted()){
                    double score=match.minimum()*0.7+match.average()*0.3;
                    if(score>bestScore){
                        best=proposed;bestScore=score;bestDx=offCx*16;bestDz=offCz*16;
                    }
                }
            }
            if(best==null){refuse("no loaded, structure-safe, terrain-compatible paired route");return;}
            logical=new Logical(epochs,loop,rear,best,bestDx,bestDz);planned++;
            if(!cfg.physical){recent="logical candidate planned; physical mode disabled";return;}
            if(active.size()>=2){refuse("occupied generation budget");return;}
            VerticalSpan span=host.verticalSpan().orElse(null);
            if(span==null){refuse("physical publication withheld: world height unknown");return;}
            List<Passage> passages=new ArrayList<>();
            for(Segment seg:best){
                V2 midpoint=seg.a().plus(seg.b()).times(.5);
                V2 tangent=seg.b().minus(seg.a()).unit();
                Surface source=new Surface(new V(midpoint.x(),span.midpoint(),midpoint.z()),
                    new V(tangent.x(),0,tangent.z()),seg.length(),span.height());
                Surface destination=new Surface(source.centre().add(new V(bestDx,0,bestDz)),source.tangent(),source.width(),source.height());
                if(!Phase3Visibility.hidden(cam,source,cfg.safetyMargin()) ||
                    !Phase3Visibility.hidden(cam,destination,cfg.safetyMargin())){
                    refuse("candidate source/destination in observer's view");return;
                }
                passages.add(new Passage(source,destination,actor.id(),epochs));
            }
            // Never stage a generation that overlaps or crosses a still-live
            // generation, even if each candidate individually passes clearance.
            for(Published old:active){
                if(!Phase3GenerationGeometry.separated(passages,old.facets,8)){
                    refuse("physical candidate overlaps earlier active generation");return;
                }
            }
            // Repeat ALL route terrain probes and complete unique-column structure checks
            // at publication time. The concrete host must also gate its atomic commit.
            // A fresh commit-time probe defeats a changed source or destination
            // after the earlier discovery/ranking pass.
            var finalTerrain=terrainAudit(best,bestDx/16,bestDz/16,new HashMap<>());
            if(!finalTerrain.accepted()){refuse("commit-time terrain veto: "+finalTerrain.reason());return;}
            if(!certify(passages)){refuse("commit-time structure veto");return;}
            // A real Host must stage ALL source/destination sections before any appear.
            // Durably write the entire intent BEFORE contacting the real renderer.
            // A failed/dirty journal blocks all physical publication.
            if(journal==null||journal.blockedOnOpen()||!journal.begin(passages)){
                disabled=true;refuse("QUARANTINED: unable to record durable publication intent");return;
            }
            Phase3Publication.Result result=Phase3Publication.publish(passages,host::stagePublication);
            if(!result.committed()){
                if(!result.cleanupConfirmed() || !journal.settle(actor.id(),epochs)){
                    disabled=true;walker.forget(actor.id());
                    uncertain.addLast(new Published(epochs,ticks,List.copyOf(passages)));
                    refuse("QUARANTINED: cleanup/journal unconfirmed: "+result.reason());
                }else refuse("atomic publication refused: "+result.reason());
                return;
            }
            active.addLast(new Published(epochs,ticks,List.copyOf(passages)));published++;
            recent="experimental bundle published";
        }catch(RuntimeException ex){
            // A renderer/camera/terrain Host failure cannot be distinguished
            // safely from invalid planner input here. Fail closed permanently.
            disabled=true;
            if(owner!=null)walker.forget(owner);
            refuse("QUARANTINED: planner/host failure: "+ex.getClass().getSimpleName());
        }
    }
}
