package dev.kondalabs.gaslight;

import java.util.*;
import dev.kondalabs.gaslight.NativePortal.*;
import dev.kondalabs.gaslight.Geometry.*;

/** Phase 2: one FIXED, bidirectional passage, prepared once, kept until shutdown or veto.
 * This is a staging rig, NOT a guarantee of seamless Minecraft renderer integration.
 * A verified static portal can later replace the 20-tick random portal scheduler.
 */
public final class FixedPortalRig {
    public enum State {DISABLED, EMPTY, LOADING, ACTIVE, REFUSED}
    public record Status(State state,String reason,int loaded,int required,int crossed){}
    private final NativeWorldEngine.Host host;
    private final boolean enabled,allowCrossing;
    private final int destDx,destDz;
    private final PortalCrossingController walker=new PortalCrossingController(15);
    private State state;
    private Passage passage;
    private UUID owner;
    private List<Chunk> required=List.of();
    private int cursor=0,crossed=0;
    private long tick=0;
    private String reason="not initialized";
    public FixedPortalRig(NativeWorldEngine.Host host,boolean enabled,boolean allowCrossing,
                          int destDx,int destDz){
        this.host=Objects.requireNonNull(host);
        this.enabled=enabled;this.allowCrossing=allowCrossing;
        if(Math.abs((long)destDx)<128||Math.abs((long)destDz)<128||
                Math.abs((long)destDx)>8_000_000||Math.abs((long)destDz)>8_000_000)
            throw new IllegalArgumentException("offset out of phase2 range");
        this.destDx=destDx;this.destDz=destDz;
        state=enabled?State.EMPTY:State.DISABLED;
    }
    public Status status(){return new Status(state,reason,cursor,required.size(),crossed);}
    public Optional<Passage> active(){return state==State.ACTIVE?Optional.of(passage):Optional.empty();}
    private void stop(String why){
        if(state==State.LOADING)host.onPortalPreparationCancelled(passage);
        if(state==State.ACTIVE)host.onPortalRetired(passage);
        if(owner!=null)walker.forget(owner);
        state=State.REFUSED;reason=why;
    }
    public void close(){
        if(state==State.LOADING)host.onPortalPreparationCancelled(passage);
        if(state==State.ACTIVE)host.onPortalRetired(passage);
        if(owner!=null)walker.forget(owner);
        passage=null;state=State.DISABLED;reason="closed";
    }
    public void tick(){
        tick++;
        if(state==State.DISABLED||state==State.REFUSED)return;
        if(!host.singleplayer()){stop("singleplayer only");return;}
        var actors=host.players();
        if(actors.size()!=1){if(state==State.ACTIVE)stop("owner disconnected");return;}
        var actor=actors.iterator().next();
        if(actor.passenger()||actor.spectator())return;
        if(state==State.EMPTY){
            owner=actor.id();
            double yaw=Math.toRadians(actor.yaw());
            V forward=new V(-Math.sin(yaw),0,Math.cos(yaw));
            V tangent=new V(Math.cos(yaw),0,Math.sin(yaw));
            V from=actor.position().add(forward.mul(6)).add(new V(0,1,0));
            V to=from.add(new V(destDx,0,destDz));
            if(Math.abs(to.x())>29_500_000||Math.abs(to.z())>29_500_000){stop("outside border");return;}
            passage=new Passage(new Surface(from,tangent,8,10),
                new Surface(to,tangent,8,10),owner,1);
            try{
                Set<Chunk> all=new LinkedHashSet<>();
                for(Surface s:List.of(passage.entrance(),passage.exit())){
                    V a=s.centre().add(s.tangent().mul(-s.width()/2));
                    V b=s.centre().add(s.tangent().mul(s.width()/2));
                    all.addAll(Geometry.chunksTouched(new Segment(new V2(a.x(),a.z()),
                        new V2(b.x(),b.z())),16.99,96));
                }
                if(all.size()>96){stop("chunk budget");return;}
                required=List.copyOf(all);state=State.LOADING;reason="certifying both portal ends";
                host.onPortalPreparing(passage);
            }catch(RuntimeException ex){stop("geometry: "+ex.getMessage());return;}
        }
        if(state==State.LOADING){
            if(cursor<required.size()){
                Chunk c=required.get(cursor);
                if(!host.loadChunk(c.x(),c.z())){stop("source or destination chunk unavailable");return;}
                cursor++;return; // strict one chunk load per tick
            }
            if(!host.portalDestinationReady(passage)){if(tick>600)stop("destination preparation timeout");return;}
            if(!certified()){stop("structure or unknown-chunk veto");return;}
            state=State.ACTIVE;reason="certified static link";
            host.onPortalActivated(passage);
        }
        if(state!=State.ACTIVE)return;
        if(!owner.equals(actor.id())){stop("ownership changed");return;}
        // Revalidate registered structure starts and references every 20 ticks.
        if(tick%20==0&&!certified()){stop("post-activation structure veto");return;}
        if(tick%40==0)host.onPortalActivated(passage); // refresh immutable remote snapshot
        if(!allowCrossing)return; // debug preview can be enabled without teleporting
        var hop=walker.visit(actor.id(),actor.position(),actor.yaw(),tick,List.of(passage));
        if(hop.isPresent()){
            var h=hop.get();
            if(host.canExit(actor,h.crossing().exitPoint())&&
                    host.teleport(actor,h.crossing().exitPoint(),h.exitYaw()))crossed++;
            else {walker.forget(actor.id());reason="crossing refused: exit blocked";}
        }
    }
    private boolean certified(){
        var inspector=(StructureEnvelope.Inspector)host::inspectChunk;
        return StructureEnvelope.audit(passage.entrance(),1,96,inspector).accepted() &&
               StructureEnvelope.audit(passage.exit(),1,96,inspector).accepted();
    }
}
