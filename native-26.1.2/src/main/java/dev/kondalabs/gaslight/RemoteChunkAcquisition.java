package dev.kondalabs.gaslight;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.resources.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.*;
import net.minecraft.world.level.chunk.status.*;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.LevelResource;
import dev.kondalabs.gaslight.RemoteChunkLeases.*;

/** Minecraft backend. Disk reads are detached; optional native generation owns only non-persistent loading tickets. */
public final class RemoteChunkAcquisition implements Backend {
    public static final TicketType TICKET=new TicketType(0,TicketType.FLAG_LOADING|TicketType.FLAG_KEEP_DIMENSION_ACTIVE);
    private static final Map<ChunkMap,NativeGeneration> OBSERVED=new ConcurrentHashMap<>();
    private final MinecraftServer server;private final Limits limits;private final boolean generationEnabled;
    private final long nativeMemoryBudget;private final ExecutorService io;
    private final AtomicInteger ioPending=new AtomicInteger();private final Map<Future<?>,Runnable> tasks=new ConcurrentHashMap<>();
    private final AtomicInteger generationStepsPerformed=new AtomicInteger();private static final AtomicLong READER_IDS=new AtomicLong();
    private final String readerPrefix="gaslight-remote-read-"+READER_IDS.incrementAndGet();
    private final List<NativeGeneration> nativeJobs=new ArrayList<>();private boolean closed;
    private final RemoteWorldBudget captureBudget=new RemoteWorldBudget(System::nanoTime);
    private long diskReads,diskNanos,peakDiskNanos,liveNanos,peakLiveNanos,ticketsAdded,ticketsRemoved;
    public RemoteChunkAcquisition(MinecraftServer server,Limits limits,boolean generationEnabled,long nativeMemoryBudget){
        this.server=server;this.limits=limits;this.generationEnabled=generationEnabled;this.nativeMemoryBudget=nativeMemoryBudget;
        if(nativeMemoryBudget<16*1_048_576L||nativeMemoryBudget>256*1_048_576L)throw new IllegalArgumentException("native memory reservation bound");
        io=Executors.newFixedThreadPool(limits.pending(),r->{Thread t=new Thread(r,readerPrefix);t.setDaemon(true);return t;});
    }
    private ServerLevel level(String dimension)throws IOException{
        if(closed)throw new IOException("acquisition backend closed");
        var level=server.getLevel(ResourceKey.create(Registries.DIMENSION,Identifier.parse(dimension)));
        if(level==null)throw new IOException("destination world unavailable");return level;
    }
    private Path region(ServerLevel level){return DimensionType.getStorageFolder(level.dimension(),server.getWorldPath(LevelResource.ROOT)).resolve("region");}
    private static ProtectedChunkRegions.Policy policy()throws IOException{
        var p=ProtectedChunkRegions.load(Path.of("config/gaslight-protected-chunks.csv"));if(!p.valid())throw new IOException(p.reason());return p;
    }
    private <T> Job<T> submit(Callable<T> action)throws IOException{
        if(closed||pending()>=limits.pending())throw new Retry("pending acquisition budget");
        var result=new CompletableFuture<T>();var slot=new AtomicReference<Future<?>>();var state=new AtomicInteger();ioPending.incrementAndGet();
        try{Future<?> future=io.submit(()->{if(!state.compareAndSet(0,1))return;long start=System.nanoTime();try{result.complete(action.call());}
            catch(Throwable e){result.completeExceptionally(e);}finally{long cost=System.nanoTime()-start;synchronized(this){diskReads++;diskNanos+=cost;peakDiskNanos=Math.max(peakDiskNanos,cost);}state.set(2);ioPending.decrementAndGet();Future<?> f=slot.get();if(f!=null)tasks.remove(f);}});
            Runnable cancel=()->{future.cancel(true);if(state.compareAndSet(0,2)){ioPending.decrementAndGet();tasks.remove(future);}result.completeExceptionally(new InterruptedIOException("read cancelled"));};
            slot.set(future);tasks.put(future,cancel);if(future.isDone())tasks.remove(future);
            return new Job<>(result,cancel);
        }catch(RejectedExecutionException e){ioPending.decrementAndGet();throw new IOException("read executor refused",e);}
    }
    private record Job<T>(CompletableFuture<T> result,Runnable cancel){}
    @Override public Read read(Key key)throws IOException{
        ServerLevel level=level(key.dimension());if(policy().protectedAt(key.x(),key.z()))throw new IOException("protected destination chunk");
        if(resident(key)){
            if(!captureBudget.canCapture())throw new Retry("preparation CPU credit");long start=System.nanoTime();
            try{return new Read(CompletableFuture.completedFuture(Optional.of(capture(key,start+RemoteWorldBudget.CAPTURE_NANOS))),()->{});}
            catch(IOException e){if(e.getMessage().contains("deadline")||e.getMessage().contains("lighting"))throw new Retry(e.getMessage());throw e;}
            finally{long cost=System.nanoTime()-start;captureBudget.captureCost(cost);liveNanos+=cost;peakLiveNanos=Math.max(peakLiveNanos,cost);}
        }
        if(level.getChunkSource().chunkMap.getUpdatingChunkIfPresent(new ChunkPos(key.x(),key.z()).pack())!=null)throw new Retry("native destination transitioning");
        Job<Optional<RemoteChunkData>> job=submit(()->{
            var pos=new ChunkPos(key.x(),key.z());var tag=RemoteRegionRead.readTag(region(level),pos);
            if(tag.isEmpty())return Optional.empty();
            return Optional.of(RemoteRegionRead.decode(level,pos,tag.get(),System.nanoTime()+250_000_000L));
        });return new Read(job.result,job.cancel);
    }
    @Override public boolean resident(Key key){try{return level(key.dimension()).getChunkSource().hasChunk(key.x(),key.z());}catch(IOException e){return false;}}
    @Override public RemoteChunkData capture(Key key,long deadline)throws IOException{return RemoteChunkCapture.capture(level(key.dimension()),key.x(),key.z(),deadline);}
    @Override public boolean generationPermitted(){return generationEnabled&&GaslightNativeMod.remoteGenerationPermitted();}
    @Override public Set<Key> footprint(Set<Key> targets)throws IOException{
        int radius=Math.max(ChunkLevel.MAX_LEVEL-ChunkLevel.byStatus(ChunkStatus.FULL),ChunkPyramid.GENERATION_PYRAMID.getStepTo(ChunkStatus.FULL).accumulatedDependencies().getRadius());
        var result=new LinkedHashSet<Key>();var p=policy();
        for(Key target:targets){ServerLevel level=level(target.dimension());
            for(int z=target.z()-radius;z<=target.z()+radius;z++)for(int x=target.x()-radius;x<=target.x()+radius;x++){
                Key key=new Key(target.dimension(),x,z);if(p.protectedAt(x,z)||!level.getWorldBorder().isWithinBounds(new ChunkPos(x,z)))throw new IOException("generation footprint protection/border veto");result.add(key);
            }}return Set.copyOf(result);
    }
    @Override public int generationStepBound(Set<Key> footprint){return Math.multiplyExact(footprint.size(),ChunkPyramid.GENERATION_PYRAMID.steps().size());}
    @Override public Generation generate(Set<Key> targets,Set<Key> footprint,int steps)throws IOException{
        if(!generationPermitted())throw new IOException("both explicit generation permissions required");
        if(nativeJobs.stream().anyMatch(g->!g.drained()))throw new IOException("native generation reservation busy/draining");
        if(generationStepsPerformed.get()+steps>limits.generationSteps())throw new IOException("remaining lifetime generation work budget");
        // Reserve memory independently of data-cache bytes, including every native dependency holder.
        if(footprint.size()*160_000L>nativeMemoryBudget)throw new IOException("native memory reservation budget");
        var job=new NativeGeneration(targets,footprint,steps);nativeJobs.add(job);return job;
    }
    @Override public int pending(){return ioPending.get()+(int)nativeJobs.stream().filter(g->g.future!=null&&!g.future.isDone()).count();}
    public static void observe(ChunkMap map,GenerationChunkHolder holder,ChunkStep step){
        NativeGeneration g=OBSERVED.get(map);if(g!=null){Key key=new Key(g.dimension,holder.getPos().x(),holder.getPos().z());
            if(g.footprint.contains(key)&&step==ChunkPyramid.GENERATION_PYRAMID.getStepTo(step.targetStatus()))g.observe(key,step);}
    }
    private final class NativeGeneration implements Generation {
        final String dimension;final ServerLevel world;final Set<Key> footprint,targets;final int stepBound;
        final List<Key> order;final Set<Key> tickets=new LinkedHashSet<>();final Set<String> steps=ConcurrentHashMap.newKeySet();
        final Job<Boolean> preflight;final long baseHeap;CompletableFuture<ChunkResult<net.minecraft.world.level.chunk.ChunkAccess>> future;
        int cursor;volatile boolean released;boolean failed,ready;String reason="generation preflight";Key current;
        NativeGeneration(Set<Key> targets,Set<Key> footprint,int bound)throws IOException{
            this.targets=Set.copyOf(targets);this.footprint=Set.copyOf(footprint);stepBound=bound;order=targets.stream().sorted(Comparator.comparingInt(Key::z).thenComparingInt(Key::x)).toList();
            dimension=order.getFirst().dimension();world=level(dimension);baseHeap=usedHeap();
            preflight=submit(()->{
                var p=policy();for(Key key:footprint){
                    if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("generation preflight cancelled");
                    if(p.protectedAt(key.x(),key.z()))throw new IOException("generation protection veto");
                    var tag=RemoteRegionRead.readTag(region(world),new ChunkPos(key.x(),key.z()));
                    if(tag.isPresent()){
                        if(tag.get().getIntOr("DataVersion",-1)!=net.minecraft.SharedConstants.getCurrentVersion().dataVersion().version())throw new IOException("existing footprint chunk requires migration");
                        if(RemoteRegionRead.hasStructures(tag.get()))throw new IOException("existing structure in generation footprint");
                    }
                }return true;
            });
        }
        void observe(Key key,ChunkStep step){steps.add(key.x()+","+key.z()+":"+step.targetStatus());generationStepsPerformed.incrementAndGet();}
        @Override public void tick(){
            if(released||failed||ready)return;
            if(!generationPermitted()){refuse("generation permission revoked");return;}
            if(usedHeap()-baseHeap>nativeMemoryBudget||Runtime.getRuntime().maxMemory()-usedHeap()<64*1_048_576L){refuse("native heap headroom/budget");return;}
            if(steps.size()>stepBound){refuse("native generation step bound exceeded");return;}
            if(!preflight.result.isDone())return;
            try{preflight.result.join();}catch(RuntimeException e){refuse("generation preflight failed: "+e.getCause());return;}
            try{
                var p=policy();for(Key key:footprint)if(p.protectedAt(key.x(),key.z()))throw new IOException("generation policy changed");
                if(current==null){
                    if(cursor>=order.size()){ready=true;reason="native destination FULL and light-correct";return;}
                    current=order.get(cursor);OBSERVED.put(world.getChunkSource().chunkMap,this);
                    world.getChunkSource().addTicketWithRadius(TICKET,new ChunkPos(current.x(),current.z()),0);tickets.add(current);ticketsAdded++;
                }
                if(future==null){
                    var holder=world.getChunkSource().chunkMap.getUpdatingChunkIfPresent(new ChunkPos(current.x(),current.z()).pack());
                    if(holder==null||holder.getTicketLevel()>ChunkLevel.byStatus(ChunkStatus.FULL))return;
                    if(pending()>=limits.pending())return;
                    future=world.getChunkSource().getChunkFuture(current.x(),current.z(),ChunkStatus.FULL,false);return;
                }
                if(!future.isDone())return;
                var chunk=future.join().orElse(null);if(chunk==null){future=null;return;}
                if(!world.getChunkSource().hasChunk(current.x(),current.z())||!chunk.isLightCorrect())return;
                future=null;current=null;cursor++;
            }catch(IOException|RuntimeException e){refuse("native generation failed: "+e);}
        }
        private void refuse(String why){failed=true;reason=why;release();}
        @Override public boolean ready(){return ready;}@Override public boolean failed(){return failed;}@Override public String reason(){return reason;}
        @Override public void release(){if(released)return;released=true;preflight.cancel.run();
            for(Key key:tickets){world.getChunkSource().removeTicketWithRadius(TICKET,new ChunkPos(key.x(),key.z()),0);ticketsRemoved++;}tickets.clear();
            System.out.println("[GASLIGHT acquisition] NATIVE_RELEASE targets="+targets.size()+" footprint="+footprint.size()+" unique_steps="+steps.size()+" reason="+reason);
        }
        @Override public boolean drained(){
            if(!released)return false;if(future!=null&&!future.isDone()||!preflight.result.isDone())return false;
            for(Key key:footprint)if(world.getChunkSource().chunkMap.getUpdatingChunkIfPresent(new ChunkPos(key.x(),key.z()).pack())!=null)return false;OBSERVED.remove(world.getChunkSource().chunkMap,this);return true;
        }
    }
    private static long usedHeap(){return Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory();}
    public long tickets(){return ticketsAdded-ticketsRemoved;}
    public String readerPrefix(){return readerPrefix;}
    public String metrics(){return "owned_tickets="+tickets()+" tickets_added="+ticketsAdded+" tickets_removed="+ticketsRemoved+" generation_steps="+generationStepsPerformed.get()+" io_pending="+ioPending.get()+" disk_reads="+diskReads+" disk_ns="+diskNanos+" peak_disk_ns="+peakDiskNanos+" live_ns="+liveNanos+" peak_live_ns="+peakLiveNanos;}
    @Override public void close(){if(closed)return;for(var g:nativeJobs){g.release();OBSERVED.remove(g.world.getChunkSource().chunkMap,g);}closed=true;for(Runnable cancel:tasks.values())cancel.run();io.shutdownNow();
        try{if(!io.awaitTermination(1,TimeUnit.SECONDS))System.err.println("[GASLIGHT acquisition] read executor still draining after shutdown");}catch(InterruptedException e){Thread.currentThread().interrupt();}
        System.out.println("[GASLIGHT acquisition] SHUTDOWN "+metrics());}
}
