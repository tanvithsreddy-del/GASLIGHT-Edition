package dev.kondalabs.gaslight;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.LongSupplier;

/** Server-thread lease book. Handles are identity-fenced; cancelled work never owns a successor. */
public final class RemoteChunkLeases implements AutoCloseable {
    public record Key(String dimension,int x,int z) {
        public Key {RemoteWorldWire.identifier(dimension);RemoteWorldWire.coordinate(x,z);}
    }
    public record Limits(int active,int requests,int pending,long dataBytes,int nativeChunks,int generationChunks,
                         int generationSteps,long preparationNanos,long freshnessNanos) {
        public Limits {
            if(active<9||active>36||requests<1||requests>8||pending<1||pending>4||dataBytes<1_048_576||dataBytes>16*1_048_576L||
               nativeChunks<0||nativeChunks>1024||generationChunks<0||generationChunks>1024||generationSteps<0||generationSteps>16384||
               preparationNanos<1_000_000_000L||preparationNanos>60_000_000_000L||freshnessNanos<1_000_000_000L||freshnessNanos>5_000_000_000L)
                throw new IllegalArgumentException("acquisition limits outside hard bounds");
        }
        public static Limits defaults(){return new Limits(18,4,2,8*1_048_576L,625,625,8192,30_000_000_000L,5_000_000_000L);}
    }
    public record Read(CompletableFuture<Optional<RemoteChunkData>> result,Runnable cancel) {}
    public static final class Retry extends IOException {public Retry(String reason){super(reason);}}
    public interface Generation {
        void tick(); boolean ready(); boolean failed(); String reason(); void release(); boolean drained();
    }
    public interface Backend {
        Read read(Key key)throws IOException;
        boolean resident(Key key);
        RemoteChunkData capture(Key key,long deadline)throws IOException;
        boolean generationPermitted();
        Set<Key> footprint(Set<Key> targets)throws IOException;
        int generationStepBound(Set<Key> footprint);
        Generation generate(Set<Key> targets,Set<Key> footprint,int stepBound)throws IOException;
        int pending();
        default void close(){}
    }
    public enum State {PREPARING,READY,FAILED,CANCELLED}
    public final class Lease {
        private final UUID id;private final long generation;private final Group group;
        private State state=State.PREPARING;private String reason="preparing";
        private Lease(UUID id,long generation,Group group){this.id=id;this.generation=generation;this.group=group;}
        public UUID id(){return id;}public long generation(){return generation;}
        public State state(){return state;}public String reason(){return reason;}
        public boolean ready(){return current(this)&&state==State.READY;}
        public Set<Key> required(){return group.keys;}
    }
    private static final class Entry {
        final Key key;int refs;RemoteChunkData data;Read work;boolean absent;long seen,retry;String error;
        Entry(Key key){this.key=key;}
    }
    private final class Group {
        final Set<Key> keys;final boolean permission;final long deadline;final List<Lease> leases=new ArrayList<>();
        Generation nativeWork;Set<Key> footprint=Set.of();boolean initialReady;
        Group(Set<Key> keys,boolean permission,long now){this.keys=Set.copyOf(keys);this.permission=permission;deadline=now+limits.preparationNanos;}
    }
    private final Backend backend;private final Limits limits;private final LongSupplier clock;
    private final Map<Key,Entry> entries=new LinkedHashMap<>();private final Map<UUID,Lease> leases=new HashMap<>();
    private final List<Group> groups=new ArrayList<>();private final List<Generation> draining=new ArrayList<>();
    private final List<Read> orphaned=new ArrayList<>();private boolean closed;
    private long bytes,peakBytes,reads,rejections,cancellations,staleReleases,generationSpent;private int reservedNative,peakActive,peakPending;
    private final Set<Key> generatedFootprint=new HashSet<>();
    public RemoteChunkLeases(Backend backend,Limits limits,LongSupplier clock){this.backend=Objects.requireNonNull(backend);this.limits=limits;this.clock=clock;}
    private boolean current(Lease lease){return lease!=null&&leases.get(lease.id)==lease;}
    public Lease prepare(UUID id,long generation,String dimension,int cx,int cz,boolean allowGeneration){
        if(id==null||generation<1)throw new IllegalArgumentException("invalid lease fence");
        Lease previous=leases.get(id);
        if(previous!=null){if(generation<=previous.generation)throw new IllegalArgumentException("stale or duplicate lease generation");release(previous);}
        var keys=new LinkedHashSet<Key>();for(int z=cz-1;z<=cz+1;z++)for(int x=cx-1;x<=cx+1;x++)keys.add(new Key(dimension,x,z));
        long now=clock.getAsLong();Group group=null;
        for(Group candidate:groups)if(candidate.keys.equals(keys)&&candidate.permission==allowGeneration){group=candidate;break;}
        if(group==null)group=new Group(keys,allowGeneration,now);
        Lease lease=new Lease(id,generation,group);
        long added=keys.stream().filter(k->!entries.containsKey(k)).count();
        if(closed||leases.size()>=limits.requests||entries.size()+added>limits.active){lease.state=State.FAILED;lease.reason="request/active chunk budget or closed";rejections++;return lease;}
        if(!groups.contains(group)){groups.add(group);for(Key key:keys)entries.computeIfAbsent(key,Entry::new).refs++;}
        leases.put(id,lease);group.leases.add(lease);peakActive=Math.max(peakActive,entries.size());return lease;
    }
    public void release(Lease lease){
        if(!current(lease)){staleReleases++;return;}
        leases.remove(lease.id);lease.state=State.CANCELLED;lease.reason="released";cancellations++;
        Group group=lease.group;group.leases.remove(lease);if(group.leases.isEmpty())removeGroup(group);
    }
    private void removeGroup(Group group){
        groups.remove(group);
        if(group.nativeWork!=null){group.nativeWork.release();draining.add(group.nativeWork);}
        for(Key key:group.keys){Entry entry=entries.get(key);if(--entry.refs>0)continue;entries.remove(key);
            if(entry.data!=null){bytes-=entry.data.residentBytes();entry.data=null;}
            if(entry.work!=null){entry.work.cancel.run();orphaned.add(entry.work);entry.work=null;}}
    }
    private void fail(Group group,String reason){
        for(Lease lease:group.leases){leases.remove(lease.id);lease.state=State.FAILED;lease.reason=reason;}
        group.leases.clear();rejections++;removeGroup(group);
    }
    public void tick(){
        if(closed)return;long now=clock.getAsLong();orphaned.removeIf(r->r.result.isDone());
        for(var it=draining.iterator();it.hasNext();){Generation g=it.next();g.tick();if(g.drained()){reservedNative-=nativeReservations.remove(g);it.remove();}}
        for(Entry entry:new ArrayList<>(entries.values()))if(entry.work!=null&&entry.work.result.isDone()){
            Read work=entry.work;entry.work=null;
            try{
                Optional<RemoteChunkData> result=work.result.join();
                if(result.isEmpty()){entry.absent=true;entry.seen=now;}
                else {RemoteChunkData data=result.get();if(data.x()!=entry.key.x||data.z()!=entry.key.z)throw new IOException("read coordinate mismatch");
                    long old=entry.data==null?0:entry.data.residentBytes(),next=bytes-old+data.residentBytes();
                    if(next>limits.dataBytes)throw new IOException("acquisition data memory budget");
                    entry.data=data;entry.absent=false;entry.seen=now;entry.error=null;bytes=next;peakBytes=Math.max(peakBytes,bytes);}
            }catch(RuntimeException|IOException e){
                // Short capture/transition failures retry only until the original wall-clock preparation deadline.
                Throwable cause=e;while(cause.getCause()!=null)cause=cause.getCause();
                entry.error=cause instanceof Retry?null:cause.toString();
                entry.retry=now+250_000_000L;if(!(cause instanceof Retry)&&entry.data!=null){bytes-=entry.data.residentBytes();entry.data=null;}entry.absent=false;
            }
        }
        for(Group group:new ArrayList<>(groups)){
            String error=group.keys.stream().map(k->entries.get(k).error).filter(Objects::nonNull).findFirst().orElse(null);
            if(error!=null){fail(group,"destination read failed: "+error);continue;}
            if(group.nativeWork!=null){group.nativeWork.tick();if(group.nativeWork.failed()){fail(group,group.nativeWork.reason());continue;}}
            boolean all=true,missing=false,probed=true;
            for(Key key:group.keys){Entry e=entries.get(key);all&=e.data!=null&&now-e.seen<=limits.freshnessNanos;missing|=e.absent;probed&=e.data!=null||e.absent;}
            if(all){group.initialReady=true;for(Lease l:group.leases){l.state=State.READY;l.reason="all required destination data ready";}}
            else if(group.initialReady){fail(group,"destination data no longer fresh/ready");continue;}
            if(!group.initialReady&&now>group.deadline){fail(group,"preparation wall-clock timeout");continue;}
            if(missing&&probed&&group.nativeWork==null){
                if(!group.permission||!backend.generationPermitted()){fail(group,"saved destination missing; generation not permitted");continue;}
                try{
                    Set<Key> footprint=backend.footprint(group.keys);int steps=backend.generationStepBound(footprint);
                    long newlyAuthorized=footprint.stream().filter(k->!generatedFootprint.contains(k)).count();
                    if(reservedNative+footprint.size()>limits.nativeChunks||generationSpent+newlyAuthorized>limits.generationChunks||steps>limits.generationSteps)
                        throw new IOException("native footprint/generation work budget");
                    group.nativeWork=backend.generate(group.keys,footprint,steps);group.footprint=footprint;
                    reservedNative+=footprint.size();generatedFootprint.addAll(footprint);generationSpent+=newlyAuthorized;nativeReservations.put(group.nativeWork,footprint.size());
                    for(Key key:group.keys){Entry e=entries.get(key);e.absent=false;e.retry=now+250_000_000L;}
                }catch(IOException|RuntimeException e){fail(group,"generation refused: "+e.getMessage());continue;}
            }
        }
        for(Entry entry:new ArrayList<>(entries.values())){
            if(backend.pending()>=limits.pending||entry.work!=null||now<entry.retry)continue;
            boolean generating=groups.stream().anyMatch(g->g.keys.contains(entry.key)&&g.nativeWork!=null&&!g.nativeWork.ready());
            if(generating)continue;
            if(entry.data!=null&&now-entry.seen<Math.min(2_000_000_000L,limits.freshnessNanos/2))continue;
            if(entry.absent)continue;
            try{entry.work=backend.read(entry.key);reads++;peakPending=Math.max(peakPending,backend.pending());}
            catch(IOException|RuntimeException e){entry.retry=now+250_000_000L;if(!(e instanceof Retry))entry.error=e.toString();}
        }
    }
    private final Map<Generation,Integer> nativeReservations=new IdentityHashMap<>();
    public RemoteChunkData data(Lease lease,int x,int z,long deadline)throws IOException{
        if(!current(lease)||!lease.group.initialReady)throw new IOException("lease/session not ready");
        Key key=new Key(lease.group.keys.iterator().next().dimension,x,z);Entry e=entries.get(key);
        if(e==null||!lease.group.keys.contains(key))throw new IOException("outside lease namespace");
        if(backend.resident(key))return backend.capture(key,deadline);
        if(e.data==null||clock.getAsLong()-e.seen>limits.freshnessNanos)throw new IOException("saved destination not fresh/available");return e.data;
    }
    public int active(){return entries.size();}public int requests(){return leases.size();}public long dataBytes(){return bytes;}
    public int nativeReserved(){return reservedNative;}
    public String metrics(){return "active="+active()+" requests="+requests()+" pending="+backend.pending()+" data_bytes="+bytes+" peak_data_bytes="+peakBytes+" peak_active="+peakActive+
        " peak_pending="+peakPending+" native_reserved="+reservedNative+" generation_reserved_lifetime="+generationSpent+" reads="+reads+" refusals="+rejections+" releases="+cancellations+" stale_releases="+staleReleases;}
    @Override public void close(){if(closed)return;for(Lease l:new ArrayList<>(leases.values()))release(l);closed=true;backend.close();draining.clear();nativeReservations.clear();reservedNative=0;orphaned.clear();}
}
