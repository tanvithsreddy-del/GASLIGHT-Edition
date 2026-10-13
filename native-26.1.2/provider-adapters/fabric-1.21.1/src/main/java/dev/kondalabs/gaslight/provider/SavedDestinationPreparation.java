package dev.kondalabs.gaslight.provider;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.nbt.*;
import net.minecraft.nbt.visitors.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.chunk.status.*;

/** Saved-data-only acquisition. No ticket is issued until every native loading
 * dependency has sufficient persisted status. Never interprets absence as air.
 * Futures are observed on the server thread; cancellation releases only our tickets. */
final class SavedDestinationPreparation implements AutoCloseable {
    enum State {SCANNING,LOADING,READY,FAILED,CANCELLED}
    record Key(ServerLevel world,int x,int z){}
    record Stats(State state,int checked,int footprint,int tickets,int pending,double elapsedMs,String reason){}
    private static final TicketType<UUID> TICKET=TicketType.create("gaslight_saved_prepare",Comparator.comparing(UUID::toString));
    private final MinecraftServer server;
    final UUID owner=UUID.randomUUID();
    private final long start=System.nanoTime(),deadline=start+30_000_000_000L;
    private final LinkedHashMap<Key,ChunkStatus> required=new LinkedHashMap<>();
    private final List<Key> targets=new ArrayList<>();
    private final Map<Key,CompletableFuture<Boolean>> scans=new LinkedHashMap<>();
    private final Map<Key,CompletableFuture<ChunkResult<ChunkAccess>>> loads=new LinkedHashMap<>();
    private final Set<Key> tickets=new LinkedHashSet<>(),loaded=new HashSet<>();
    private int cursor,checked,loadCursor;
    private State state=State.SCANNING;private String reason="checking persisted native dependency statuses";
    SavedDestinationPreparation(MinecraftServer server,FixedPortalAdapter.Spec spec){
        this.server=server;
        int halo=ChunkPyramid.LOADING_PYRAMID.getStepTo(ChunkStatus.FULL).accumulatedDependencies().getRadius();
        if(halo<0||halo>3)throw new IllegalArgumentException("unsupported native loading footprint");
        for(var side:List.of(Map.entry(spec.source(),spec.origin()),Map.entry(spec.target(),spec.destination()))){
            if(side.getKey().getServer()!=server||!Double.isFinite(side.getValue().x)||!Double.isFinite(side.getValue().z)||Math.abs(side.getValue().x)>29_000_000||Math.abs(side.getValue().z)>29_000_000)throw new IllegalArgumentException("world/coordinate fence");
            int cx=Math.floorDiv((int)Math.floor(side.getValue().x),16),cz=Math.floorDiv((int)Math.floor(side.getValue().z),16);
            for(int x=cx-2;x<=cx+2;x++)for(int z=cz-2;z<=cz+2;z++){
                Key target=new Key(side.getKey(),x,z);if(!targets.contains(target))targets.add(target);
                var dependencies=ChunkPyramid.LOADING_PYRAMID.getStepTo(ChunkStatus.FULL).accumulatedDependencies();
                for(int dx=-halo;dx<=halo;dx++)for(int dz=-halo;dz<=halo;dz++){
                    var key=new Key(side.getKey(),x+dx,z+dz);int distance=Math.max(Math.abs(dx),Math.abs(dz));
                    ChunkStatus status=dependencies.get(distance);
                    required.merge(key,status,(a,b)->a.isOrAfter(b)?a:b);
                }
            }
        }
        if(targets.size()>50||required.size()>98)throw new IllegalArgumentException("native acquisition footprint budget");
    }
    private void thread(){if(!server.isSameThread())throw new IllegalStateException("server thread required");}
    Stats stats(){thread();return new Stats(state,checked,required.size(),tickets.size(),scans.size()+loads.size(),(System.nanoTime()-start)/1e6,reason);}
    State state(){return state;}
    void tick(){
        thread();if(state==State.FAILED||state==State.CANCELLED||state==State.READY)return;
        if(System.nanoTime()>deadline){fail("preparation deadline");return;}
        try{
            if(state==State.SCANNING){
                for(var it=scans.entrySet().iterator();it.hasNext();){var e=it.next();if(e.getValue().isDone()){
                    if(!e.getValue().join()){fail("saved destination or dependency unavailable: "+e.getKey());return;}
                    checked++;it.remove();
                }}
                var keys=new ArrayList<>(required.keySet());
                while(scans.size()<2&&cursor<keys.size()){
                    Key key=keys.get(cursor++);ChunkStatus minimum=required.get(key);
                    var resident=key.world.getChunkSource().getChunkNow(key.x,key.z);
                    if(resident!=null&&resident.getPersistedStatus().isOrAfter(minimum)){checked++;continue;}
                    CollectFields collector=new CollectFields(new FieldSelector(StringTag.TYPE,"Status"));
                    scans.put(key,key.world.getChunkSource().chunkScanner().scanChunk(new ChunkPos(key.x,key.z),collector).thenApply(ignored->{
                        if(!(collector.getResult() instanceof CompoundTag tag)||!tag.contains("Status",Tag.TAG_STRING))return false;
                        ChunkStatus actual=ChunkStatus.byName(tag.getString("Status"));return actual!=null&&actual.isOrAfter(minimum);
                    }));
                }
                if(checked==required.size()){state=State.LOADING;reason="saved dependencies certified; bounded ticket acquisition";}
            }
            if(state==State.LOADING){
                for(var it=loads.entrySet().iterator();it.hasNext();){var e=it.next();if(!e.getValue().isDone())continue;
                    var result=e.getValue().join();it.remove();
                    if(result.isSuccess()&&result.orElse(null) instanceof LevelChunk)loaded.add(e.getKey());
                    // A false-create request may precede the distance-manager update.
                    // Keep the owned ticket, retry later, and retain the original deadline.
                }
                while(loads.size()<2&&loadCursor<targets.size()){
                    Key key=targets.get(loadCursor++);
                    if(loaded.contains(key))continue;
                    if(tickets.add(key))key.world.getChunkSource().addRegionTicket(TICKET,new ChunkPos(key.x,key.z),0,owner);
                    loads.put(key,key.world.getChunkSource().getChunkFuture(key.x,key.z,ChunkStatus.FULL,false));
                }
                if(loadCursor==targets.size()&&loads.isEmpty()){
                    if(loaded.size()==targets.size()){state=State.READY;reason="all target chunks FULL; safety audit still required";}
                    else loadCursor=0;
                }
            }
        }catch(RuntimeException failure){fail("native acquisition failure: "+failure);}
    }
    private void fail(String why){reason=why;state=State.FAILED;release();}
    private void release(){for(Key key:tickets)key.world.getChunkSource().removeRegionTicket(TICKET,new ChunkPos(key.x,key.z),0,owner);tickets.clear();scans.clear();loads.clear();}
    @Override public void close(){thread();if(state!=State.FAILED){state=State.CANCELLED;reason="released";}release();}
}
