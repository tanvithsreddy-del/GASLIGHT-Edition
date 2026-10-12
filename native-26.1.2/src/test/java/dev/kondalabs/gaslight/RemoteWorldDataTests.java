package dev.kondalabs.gaslight;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.DeflaterOutputStream;
import static dev.kondalabs.gaslight.RemoteWorldWire.*;
public final class RemoteWorldDataTests {
    private static int assertions;
    private static void check(boolean b,String message){assertions++;if(!b)throw new AssertionError(message);}
    private static void refuses(Action a,String message)throws Exception{try{a.run();throw new AssertionError(message);}catch(IOException|IllegalArgumentException expected){assertions++;}}
    private interface Action {void run()throws Exception;}
    private static Scope scope(UUID owner,long epoch,String dst){
        var a=new NativePortal.Surface(new NativePortal.V(0.5,68,0.5),new NativePortal.V(1,0,0),8,10);
        var b=new NativePortal.Surface(new NativePortal.V(1024.5,68,1024.5),new NativePortal.V(1,0,0),8,10);
        return new Scope(UUID.randomUUID(),UUID.randomUUID(),owner,epoch,"minecraft:overworld",dst,"minecraft:overworld",64,64,-64,24,new NativePortal.Passage(a,b,owner,1));
    }
    private static RemoteChunkData data(int x,int z,int marker,boolean noisy){
        var random=new Random(4);int[] blocks=new int[24*4096],biomes=new int[24*64];Arrays.fill(blocks,marker);
        byte[][] sky=new byte[26][2048],light=new byte[26][2048];for(var a:sky)Arrays.fill(a,(byte)0xff);for(var a:light)Arrays.fill(a,(byte)0xcc);
        if(noisy){for(int k=0;k<blocks.length;k++)blocks[k]=random.nextInt(8192);for(var a:light)random.nextBytes(a);}
        return new RemoteChunkData(x,z,-64,24,blocks,biomes,sky,light,List.of(new RemoteChunkData.BlockEntityData(x*16,68,z*16,"minecraft:sign",new byte[]{10,0,0,0})),Map.of("WORLD_SURFACE",new long[]{1,2}));
    }
    private static List<Packet> packets(Scope s,RemoteChunkData data,long rev)throws IOException{return fragments(s,data.x(),data.z(),rev,RemoteChunkData.compress(data.raw()));}
    private static void accept(RemoteWorldCache c,Scope s,Packet p)throws IOException{c.accept(p,s.owner(),s.sourceDimension(),d->{},a->{});}
    public static void main(String[] ignored)throws Exception{
        assertions=0;UUID owner=UUID.randomUUID();Scope s=scope(owner,1,"minecraft:the_nether");AtomicLong clock=new AtomicLong(1);var cache=new RemoteWorldCache(clock::get);
        check(decode(encode(control(Kind.OPEN,s,64,64,0))).scope().equals(s),"scope roundtrip");
        accept(cache,s,control(Kind.OPEN,s,64,64,0));var initial=data(64,64,9,false);var first=packets(s,initial,1);
        for(var p:first)accept(cache,s,decode(encode(p)));
        check(cache.chunkCount()==1,"initial load");var loaded=cache.chunk(s.destinationDimension(),64,64).orElseThrow();
        check(loaded.stateId(1024,68,1024)==9,"real state id preserved");check(loaded.light(true,1024,68,1024)==15&&loaded.light(false,1024,68,1024)==12,"light nibbles preserved");
        check(loaded.blockEntities().size()==1&&loaded.blockEntities().getFirst().type().equals("minecraft:sign"),"block entity render tag preserved");
        check(loaded.heightmaps().get("WORLD_SURFACE")[1]==2,"heightmaps preserved");
        check(cache.chunk("minecraft:overworld",64,64).isEmpty(),"remote dimension never nearby namespace");
        check(!cache.accept(control(Kind.OPEN,s,64,64,0),UUID.randomUUID(),s.sourceDimension(),d->{},a->{}),"wrong owner rejected");
        check(!cache.accept(control(Kind.OPEN,s,64,64,0),owner,"minecraft:the_end",d->{},a->{}),"wrong source dimension rejected");
        for(var p:packets(s,data(64,64,10,false),2))accept(cache,s,p);
        check(cache.chunk(s.destinationDimension(),64,64).orElseThrow().stateId(1024,68,1024)==10,"update replaces old complete revision");
        for(var p:first)accept(cache,s,p);
        check(cache.chunk(s.destinationDimension(),64,64).orElseThrow().stateId(1024,68,1024)==10,"stale update cannot resurrect");
        accept(cache,s,control(Kind.UNAVAILABLE,s,64,64,3));check(cache.chunkCount()==0,"unavailable clears data");
        for(var p:packets(s,initial,3))accept(cache,s,p);check(cache.chunkCount()==0,"unavailable tombstone rejects same-revision chunks");
        List<Packet> noisy=packets(s,data(64,64,0,true),4);check(noisy.size()>1,"fragment test genuinely fragmented");
        accept(cache,s,noisy.getFirst());check(cache.chunkCount()==0,"partial revision invisible");
        clock.addAndGet(2_100_000_000L);cache.expire();check(cache.stagingBytes()==0,"lost fragment staging expires");
        var reversed=new ArrayList<>(noisy);Collections.reverse(reversed);for(var p:reversed)accept(cache,s,p);
        check(cache.chunkCount()==1,"retransmit and reordering recover complete snapshot");
        accept(cache,s,control(Kind.INVALIDATE,s,64,64,4));check(cache.chunkCount()==1,"late invalidation cannot erase already committed revision");
        int used=cache.cacheBytes();for(var p:noisy)accept(cache,s,p);check(cache.cacheBytes()==used,"duplicate fragments do not grow memory");
        var p=noisy.getFirst();byte[] changed=p.bytes();changed[0]^=1;
        Packet corrupt=new Packet(p.kind(),p.session(),p.epoch(),p.x(),p.z(),5,p.index(),p.parts(),p.digest(),changed,null);
        accept(cache,s,corrupt);for(int k=1;k<noisy.size()-1;k++){var q=noisy.get(k);accept(cache,s,new Packet(q.kind(),q.session(),q.epoch(),q.x(),q.z(),5,q.index(),q.parts(),q.digest(),q.bytes(),null));}
        var end=noisy.getLast();refuses(()->accept(cache,s,new Packet(end.kind(),end.session(),end.epoch(),end.x(),end.z(),5,end.index(),end.parts(),end.digest(),end.bytes(),null)),"bad digest rejected");
        check(cache.chunkCount()==0&&cache.scope().isEmpty(),"recovery failure clears render data");
        Scope fresh=scope(owner,2,"minecraft:the_nether");accept(cache,fresh,control(Kind.OPEN,fresh,64,64,0));
        for(var q:first)accept(cache,fresh,q);check(cache.chunkCount()==0,"previous-session replay rejected");
        for(var q:packets(fresh,initial,1))accept(cache,fresh,q);
        clock.addAndGet(TTL_NANOS+1);check(cache.chunkCount()==0&&cache.scope().isEmpty(),"lost close/heartbeat expires fail closed");
        cache.disconnect();Scope reconnect=scope(owner,1,"minecraft:the_nether");accept(cache,reconnect,control(Kind.OPEN,reconnect,64,64,0));check(cache.scope().isPresent(),"reconnect resets connection epoch watermark");
        accept(cache,reconnect,control(Kind.CLOSE,reconnect,64,64,0));check(cache.chunkCount()==0&&cache.stagingBytes()==0,"close unloads cache and staging");
        var bytes=encode(first.getFirst());for(int n=0;n<bytes.length;n++){byte[] cut=Arrays.copyOf(bytes,n);refuses(()->decode(cut),"partial wire record");}
        byte[] oversized=new byte[MAX_PACKET+1];refuses(()->decode(oversized),"outer allocation bound");
        byte[] raw=initial.raw();for(int offset:new int[]{0,4,8,12,16}){byte[] bad=raw.clone();Arrays.fill(bad,offset,offset+4,(byte)0xff);refuses(()->RemoteChunkData.decodeRaw(bad),"invalid header dimensions/count");}
        byte[] giant=new byte[RemoteChunkData.MAX_RAW+1];var compressed=new ByteArrayOutputStream();try(var z=new DeflaterOutputStream(compressed)){z.write(giant);}
        refuses(()->RemoteChunkData.inflate(compressed.toByteArray()),"decompression bomb bounded");
        byte[] zipped=RemoteChunkData.compress(raw);refuses(()->RemoteChunkData.inflate(Arrays.copyOf(zipped,zipped.length-1)),"partial deflate rejected");
        refuses(()->RemoteChunkData.inflate(Arrays.copyOf(zipped,zipped.length+1)),"trailing deflate rejected");
        AtomicLong time=new AtomicLong();var budget=new RemoteWorldBudget(time::get);
        check(budget.send((int)RemoteWorldBudget.BURST_BYTES)&&!budget.send(1),"strict initial burst");
        for(int k=0;k<100;k++)check(!budget.send(1),"fast ticks cannot mint bandwidth");
        time.set(1_000_000_000);check(!budget.send((int)RemoteWorldBudget.BURST_BYTES+1)&&budget.send((int)RemoteWorldBudget.BURST_BYTES),"wall time refill remains capped");
        check(budget.canCapture(),"initial CPU credit");budget.captureCost(RemoteWorldBudget.CAPTURE_NANOS);check(!budget.canCapture(),"CPU throttled");
        time.addAndGet(400_000_000);check(budget.canCapture(),"CPU wall-time refill");time.set(0);check(!budget.send(1)&&!budget.canCapture(),"clock rollback fails closed");
        System.out.println("REMOTE_WORLD_DATA_PASS: assertions="+assertions+" fragments="+noisy.size()+" peak_cache_bytes="+cache.peakCacheBytes()+" peak_staging_bytes="+cache.peakStagingBytes());
    }
}
