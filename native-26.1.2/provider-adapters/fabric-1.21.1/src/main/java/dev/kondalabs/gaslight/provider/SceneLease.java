package dev.kondalabs.gaslight.provider;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.server.level.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.chunk.status.*;

/** Native generation is allowed only in GASLIGHT's private dimension. The native
 * dependency footprint is admitted before a ticket; requests never mean air. */
final class SceneLease implements AutoCloseable {
    private static final TicketType<UUID> TICKET=TicketType.create("gaslight_scene",Comparator.comparing(UUID::toString));
    private final ServerLevel world;
    private final UUID owner=UUID.randomUUID();
    private final List<ChunkPos> targets=new ArrayList<>();
    private final Set<ChunkPos> tickets=new HashSet<>(),ready=new HashSet<>();
    private final Map<ChunkPos,CompletableFuture<ChunkResult<ChunkAccess>>> pending=new HashMap<>();
    private final long deadline=System.nanoTime()+45_000_000_000L;
    private int cursor;
    final int dependencies;
    private boolean closed;
    private final boolean requireUnvisited;
    private int readCursor,readDone;
    private final Map<ChunkPos,CompletableFuture<Optional<net.minecraft.nbt.CompoundTag>>> reads=new HashMap<>();
    SceneLease(ServerLevel world,int cx,int cz,int radius) {
        this(world,cx,cz,radius,false);
    }
    SceneLease(ServerLevel world,int cx,int cz,int radius,boolean requireUnvisited) {
        if(!world.dimension().equals(AutomaticPortalService.WORLD)||radius<1||radius>4)throw new IllegalArgumentException("generation/world budget");
        this.world=world;
        this.requireUnvisited=requireUnvisited;
        int halo=ChunkPyramid.GENERATION_PYRAMID.getStepTo(ChunkStatus.FULL).accumulatedDependencies().getRadius();
        dependencies=(2*(radius+halo)+1)*(2*(radius+halo)+1);
        if(halo>12||dependencies>1024)throw new IllegalArgumentException("native dependency budget");
        for(int x=cx-radius;x<=cx+radius;x++)for(int z=cz-radius;z<=cz+radius;z++) {
            if(!world.getWorldBorder().isWithinBounds(new net.minecraft.core.BlockPos(x*16,64,z*16)))throw new IllegalArgumentException("world border");
            targets.add(new ChunkPos(x,z));
        }
    }
    boolean tick() {
        if(closed||System.nanoTime()>deadline)throw new IllegalStateException("preparation closed/timed out");
        if(requireUnvisited&&readDone<targets.size()){
            for(var it=reads.entrySet().iterator();it.hasNext();){var e=it.next();if(!e.getValue().isDone())continue;var saved=e.getValue().join();it.remove();
                if(saved.isPresent()){
                    String status=saved.get().getString("Status");
                    if(status.isEmpty()||status.equals("minecraft:full")||status.equals("full"))throw new IllegalStateException("private reservation already FULL or unreadable: "+e.getKey());
                }
                readDone++;
            }
            while(reads.size()<2&&readCursor<targets.size()){
                var pos=targets.get(readCursor++);
                if(world.getChunkSource().getChunkNow(pos.x,pos.z)!=null)throw new IllegalStateException("private reservation already loaded/visited: "+pos);
                reads.put(pos,world.getChunkSource().chunkMap.read(pos));
            }
            if(readDone<targets.size())return false;
        }
        for(var it=pending.entrySet().iterator();it.hasNext();) {
            var e=it.next();if(!e.getValue().isDone())continue;
            var result=e.getValue().join();it.remove();
            if(result.isSuccess()&&result.orElse(null) instanceof LevelChunk)ready.add(e.getKey());
        }
        while(pending.size()<2&&cursor<targets.size()) {
            var p=targets.get(cursor++);if(ready.contains(p))continue;
            if(tickets.add(p))world.getChunkSource().addRegionTicket(TICKET,p,0,owner);
            pending.put(p,world.getChunkSource().getChunkFuture(p.x,p.z,ChunkStatus.FULL,true));
        }
        if(cursor==targets.size()&&pending.isEmpty()&&ready.size()!=targets.size())cursor=0;
        return ready.size()==targets.size()&&!world.getChunkSource().getLightEngine().hasLightWork();
    }
    int tickets(){return tickets.size();}
    @Override public void close() {
        if(closed)return;closed=true;
        for(var p:tickets)world.getChunkSource().removeRegionTicket(TICKET,p,0,owner);
        tickets.clear();pending.clear();reads.clear();
    }
}
