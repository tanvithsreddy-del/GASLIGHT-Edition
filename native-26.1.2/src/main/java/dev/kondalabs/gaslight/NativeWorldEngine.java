package dev.kondalabs.gaslight;

import java.util.*;
import dev.kondalabs.gaslight.Geometry.*;
import dev.kondalabs.gaslight.NativePortal.*;

/** GASLIGHT-native game-independent server controller. All world data access is via Host.
 * Generation is strictly periodic, physical activation requires loaded and certified chunks.
 */
public final class NativeWorldEngine {
    public interface Host {
        Collection<Actor> players();
        // Safe to call at most once per tick; may generate chunk, so host must budget work.
        boolean loadChunk(int cx,int cz);
        StructureEnvelope.Result inspectChunk(int cx,int cz);
        boolean canExit(Actor actor, V target);
        boolean teleport(Actor actor,V target,double yaw);
        boolean singleplayer();
        /** Read-only preview notification. Never affects physical portal certification. */
        default void onPortalActivated(Passage passage) {}
        default void onPortalPreparing(Passage passage) {}
        default void onPortalPreparationCancelled(Passage passage) {}
        default boolean portalDestinationReady(Passage passage){return true;}
        default void onPortalRetired(Passage passage) {}
    }
    public record Actor(UUID id,V position,double yaw,double pitch,boolean spectator,boolean passenger) {}
    public record Snapshot(long ticks,long generations,long activated,long crossings,long refused,
                           int waiting,int active,String recentRefusal){}
    private static final int PHYSICAL_CAP=2,MAX_PENDING=2,REAR_DISTANCE=80,WIDTH=12;
    private static final int MAX_SCAN=96,HALO=1;
    private static final long AGE_LIMIT=60;
    private final Host host;
    private final PortalCrossingController crossing=new PortalCrossingController(12);
    private final ArrayDeque<Job> pending=new ArrayDeque<>();
    private final List<Live> live=new ArrayList<>();
    private final Set<UUID> trackedPlayers=new HashSet<>();
    private long ticks=0,generations=0,activated=0,crossings=0,refused=0;
    private String recentRefusal="none";
    private boolean enabled;
    private long randomSeed;
    private record Live(Passage passage,long createdAt){}
    private static final class Job {
        final Passage passage;
        final List<Geometry.Chunk> needs;
        final UUID owner;
        int cursor;
        Job(Passage p,List<Geometry.Chunk> chunks){passage=p;needs=chunks;owner=p.owner();}
    }
    public NativeWorldEngine(Host host,long worldSeed,boolean enabled){
        this.host=Objects.requireNonNull(host);this.randomSeed=worldSeed;this.enabled=enabled;
    }
    public void enabled(boolean value){
        enabled=value;
        if(!value){for(Live l:live)host.onPortalRetired(l.passage());
            live.clear();for(Job job:pending)host.onPortalPreparationCancelled(job.passage);pending.clear();trackedPlayers.clear();}
    }
    public boolean enabled(){return enabled;}
    public Snapshot snapshot(){return new Snapshot(ticks,generations,activated,crossings,refused,pending.size(),live.size(),recentRefusal);}
    private void reject(String cause){refused++;recentRefusal=cause;}
    public void tick(){
        ticks++;
        if(!enabled)return;
        if(!host.singleplayer()){reject("multiplayer unsupported");enabled(false);return;}
        Collection<Actor> people=host.players();
        if(people.size()!=1){return;}
        Actor user=people.iterator().next();
        trackedPlayers.add(user.id());
        if(ticks%20==0){
            generations++;
            if(pending.size()<MAX_PENDING && !user.spectator() && !user.passenger())
                tryPrepare(user);
        }
        // Never activate a portal before destination load and hard structure certification.
        processOnePendingChunk();
        // Avoid removing a portal while the player stands near either end.
        for(Iterator<Live> it=live.iterator();it.hasNext();){
            Live item=it.next();
            if(ticks-item.createdAt>=AGE_LIMIT && !near(user.position(),item.passage,8)){
                it.remove();host.onPortalRetired(item.passage());
            }
        }
        if(user.spectator()||user.passenger()){crossing.forget(user.id());return;}
        List<Passage> portals=new ArrayList<>();
        for(Live l:live)portals.add(l.passage());
        Optional<PortalCrossingController.Hop> hop=crossing.visit(user.id(),user.position(),user.yaw(),ticks,portals);
        if(hop.isPresent()){
            PortalCrossingController.Hop h=hop.get();
            if(host.canExit(user,h.crossing().exitPoint()) && host.teleport(user,h.crossing().exitPoint(),h.exitYaw()))crossings++;
            else {crossing.forget(user.id());reject("exit blocked or teleport failed");}
        }
    }
    private static boolean near(V p,Passage passage,double radius){
        return nearSurface(p,passage.entrance(),radius)||nearSurface(p,passage.exit(),radius);
    }
    private static boolean nearSurface(V p, Surface s,double r){
        return Math.abs(s.side(p))<r && Math.abs(s.across(p))<s.width()/2+r &&
               Math.abs(s.altitude(p))<s.height()/2+r;
    }
    private void tryPrepare(Actor user){
        // Deterministic seeded distant offsets (avoid unpredictable global RNG state).
        int cx=(int)Math.floor(user.position().x()/16), cz=(int)Math.floor(user.position().z()/16);
        long entropy=PortalCandidate.hashCell(randomSeed ^ generations,cx,cz);
        double dx=(1_000_000L+Math.floorMod(entropy,6_000_000L))*(entropy<0?-1:1);
        double dz=(1_000_000L+Math.floorMod(entropy >>> 23,6_000_000L))*((entropy&2)==0?-1:1);
        // Confine to world border; no floating-point overflow or teleport beyond border.
        if(Math.abs(user.position().x()+dx)>29_500_000||Math.abs(user.position().z()+dz)>29_500_000){reject("world border");return;}
        Passage p;
        try{p=PortalCandidate.fromRear(new V(user.position().x(),128,user.position().z()),user.yaw(),
                        (float)user.pitch(),dx,dz,REAR_DISTANCE,WIDTH,384,generations,user.id());}
        catch(RuntimeException e){reject("candidate math: "+e.getMessage());return;}
        List<Geometry.Chunk> needs=new ArrayList<>();
        for(Surface s:List.of(p.entrance(),p.exit())){
            V first=s.centre().add(s.tangent().mul(-s.width()/2)),last=s.centre().add(s.tangent().mul(s.width()/2));
            try{
                needs.addAll(Geometry.chunksTouched(new Segment(new V2(first.x(),first.z()),new V2(last.x(),last.z())),
                      HALO*16+.99,MAX_SCAN));
            } catch(Exception e){reject("chunk budget: "+e.getMessage());return;}
        }
        if(needs.size()>MAX_SCAN){reject("too many chunks");return;}
        LinkedHashSet<Geometry.Chunk> unique=new LinkedHashSet<>(needs);
        pending.add(new Job(p,new ArrayList<>(unique)));
        host.onPortalPreparing(p);
    }
    private void processOnePendingChunk(){
        Job job=pending.peek();if(job==null)return;
        if(job.cursor<job.needs.size()){
            Geometry.Chunk c=job.needs.get(job.cursor);
            if(!host.loadChunk(c.x(),c.z())) {pending.remove();host.onPortalPreparationCancelled(job.passage);reject("chunk load failed");return;}
            job.cursor++;
            return;
        }
        pending.remove();
        if(!host.portalDestinationReady(job.passage)){host.onPortalPreparationCancelled(job.passage);reject("destination data not ready");return;}
        StructureEnvelope.Inspector inspector=host::inspectChunk;
        StructureEnvelope.Audit src=StructureEnvelope.audit(job.passage.entrance(),HALO,MAX_SCAN,inspector);
        StructureEnvelope.Audit dst=StructureEnvelope.audit(job.passage.exit(),HALO,MAX_SCAN,inspector);
        if(!src.accepted()||!dst.accepted()){
            host.onPortalPreparationCancelled(job.passage);
            reject("structure veto: "+src.reason()+" / "+dst.reason());return;
        }
        // Whole-world plane is mathematical; this is only a 12-block portal section.
        // Limit live pairs; refuse rather than remove occupied connections.
        if(live.size()>=PHYSICAL_CAP){
            host.onPortalPreparationCancelled(job.passage);
            reject("live portal budget");return;
        }
        live.add(new Live(job.passage,ticks)); activated++;
        host.onPortalActivated(job.passage);
    }
}
