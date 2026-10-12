package dev.kondalabs.gaslight;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.io.IOException;
import static dev.kondalabs.gaslight.RemoteChunkLeases.*;
/** Lifecycle model tests. They deliberately do not claim Minecraft loading proof. */
public final class RemoteChunkLeaseTests {
    private static int checks;private static void check(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
    private static RemoteChunkData data(Key k,int n){return new RemoteChunkData(k.x(),k.z(),0,n,new int[n*4096],new int[n*64],new byte[n+2][2048],new byte[n+2][2048],List.of(),Map.of());}
    private static final class Fake implements Backend {
        final Map<Key,CompletableFuture<Optional<RemoteChunkData>>> reads=new LinkedHashMap<>();
        boolean missing,permission,slow,failed,drained=true;int cancelled,generated,activeGeneration,n=1;Set<Key> generatedKeys=Set.of();
        public Read read(Key k){var f=new CompletableFuture<Optional<RemoteChunkData>>();reads.put(k,f);
            if(!slow){if(failed)f.completeExceptionally(new IOException("injected read failure"));else f.complete(missing&&!generatedKeys.contains(k)?Optional.empty():Optional.of(data(k,n)));}
            return new Read(f,()->{cancelled++;});}
        public boolean resident(Key k){return false;}public RemoteChunkData capture(Key k,long deadline){return data(k,n);}
        public boolean generationPermitted(){return permission;}
        public Set<Key> footprint(Set<Key> keys){var r=new HashSet<Key>();for(Key k:keys)for(int z=k.z()-11;z<=k.z()+11;z++)for(int x=k.x()-11;x<=k.x()+11;x++)r.add(new Key(k.dimension(),x,z));return r;}
        public int generationStepBound(Set<Key> keys){return keys.size()*12;}
        public Generation generate(Set<Key> keys,Set<Key> footprint,int bound){generated++;activeGeneration++;generatedKeys=keys;
            return new Generation(){boolean release;public void tick(){}public boolean ready(){return true;}public boolean failed(){return false;}public String reason(){return "";}
                public void release(){if(!release){release=true;activeGeneration--;}}public boolean drained(){return release&&Fake.this.drained;}};}
        public int pending(){return (int)reads.values().stream().filter(f->!f.isDone()).count();}
    }
    private static void ticks(RemoteChunkLeases book,AtomicLong clock,int n){for(int i=0;i<n;i++){clock.addAndGet(50_000_000);book.tick();}}
    public static void main(String[] args)throws Exception {
        var clock=new AtomicLong();var fake=new Fake();var limits=Limits.defaults();var book=new RemoteChunkLeases(fake,limits,clock::get);
        UUID id=UUID.randomUUID();var a=book.prepare(id,1,"minecraft:the_nether",0,0,false);var b=book.prepare(UUID.randomUUID(),1,"minecraft:the_nether",0,0,false);
        ticks(book,clock,15);check(a.ready()&&b.ready(),"saved aliases ready");check(book.active()==9,"shared ownership uses nine entries");
        book.release(a);check(b.ready()&&book.active()==9,"first release must not unload another owner");
        var successor=book.prepare(id,2,"minecraft:the_nether",0,0,false);ticks(book,clock,2);book.release(a);check(successor.ready()&&b.ready(),"stale cleanup cannot release successor");
        book.release(successor);book.release(b);check(book.active()==0&&book.dataBytes()==0,"last reference deterministic cleanup");
        for(int cycle=0;cycle<30;cycle++){var l=book.prepare(UUID.randomUUID(),1,"minecraft:the_nether",0,0,false);ticks(book,clock,15);check(l.ready(),"repeated load");book.release(l);check(book.active()==0,"repeated unload");}
        book.close();
        fake=new Fake();fake.slow=true;book=new RemoteChunkLeases(fake,limits,clock::get);id=UUID.randomUUID();a=book.prepare(id,1,"minecraft:overworld",100,100,false);ticks(book,clock,2);
        check(fake.pending()==2,"pending load cap");book.release(a);check(book.active()==0&&fake.cancelled==2,"cancel loading clears ownership");
        var late=new ArrayList<>(fake.reads.entrySet());for(var r:late)r.getValue().complete(Optional.of(data(r.getKey(),1)));
        fake.slow=false;b=book.prepare(id,2,"minecraft:overworld",100,100,false);ticks(book,clock,15);check(b.ready(),"successor after late completions");book.release(a);check(b.ready(),"outdated release ignored");book.close();
        fake=new Fake();fake.slow=true;book=new RemoteChunkLeases(fake,limits,clock::get);a=book.prepare(UUID.randomUUID(),1,"minecraft:overworld",0,0,false);ticks(book,clock,610);
        check(a.state()==State.FAILED&&a.reason().contains("timeout")&&book.active()==0,"slow read timeout fails closed");book.close();
        fake=new Fake();fake.missing=true;book=new RemoteChunkLeases(fake,limits,clock::get);a=book.prepare(UUID.randomUUID(),1,"minecraft:overworld",0,0,false);ticks(book,clock,15);
        check(a.state()==State.FAILED&&fake.generated==0,"missing is not empty terrain and no generation without permission");book.close();
        fake=new Fake();fake.missing=true;fake.permission=true;book=new RemoteChunkLeases(fake,limits,clock::get);a=book.prepare(UUID.randomUUID(),1,"minecraft:overworld",0,0,true);ticks(book,clock,25);
        check(a.ready()&&fake.generated==1&&book.nativeReserved()==625,"generation reserves complete native halo");
        fake.drained=false;book.release(a);check(fake.activeGeneration==0&&book.nativeReserved()==625,"reservation retained while cancelled native work drains");ticks(book,clock,3);
        b=book.prepare(UUID.randomUUID(),1,"minecraft:overworld",100,100,true);ticks(book,clock,20);check(b.state()==State.FAILED&&fake.generated==1,"generation cannot exceed lifetime/halo budgets");
        fake.drained=true;ticks(book,clock,1);check(book.nativeReserved()==0,"drain reservation released");book.close();
        fake=new Fake();fake.failed=true;book=new RemoteChunkLeases(fake,limits,clock::get);a=book.prepare(UUID.randomUUID(),1,"minecraft:overworld",0,0,false);ticks(book,clock,3);
        check(a.state()==State.FAILED&&book.dataBytes()==0,"read failure never publishes");book.close();
        fake=new Fake();fake.n=32;var tiny=new Limits(18,4,2,1_048_576,625,625,8192,30_000_000_000L,5_000_000_000L);
        book=new RemoteChunkLeases(fake,tiny,clock::get);a=book.prepare(UUID.randomUUID(),1,"minecraft:overworld",0,0,false);ticks(book,clock,15);
        check(a.state()==State.FAILED&&book.dataBytes()==0,"data-memory exhaustion fails closed");book.close();
        fake=new Fake();book=new RemoteChunkLeases(fake,limits,clock::get);
        a=book.prepare(UUID.randomUUID(),1,"minecraft:overworld",0,0,false);b=book.prepare(UUID.randomUUID(),1,"minecraft:overworld",100,100,false);
        var rejected=book.prepare(UUID.randomUUID(),1,"minecraft:overworld",200,200,false);check(rejected.state()==State.FAILED&&book.active()==18,"active chunk exhaustion");
        ticks(book,clock,15);check(a.ready()&&b.ready(),"two independent simultaneous preparations");book.close();check(book.active()==0&&book.requests()==0,"shutdown cleanup");
        System.out.println("REMOTE_CHUNK_LEASE_MODEL_PASS: checks="+checks+" cycles=30");
    }
}
