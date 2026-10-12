package dev.kondalabs.gaslight;

import java.io.*;
import java.util.*;
import java.util.function.*;
import static dev.kondalabs.gaslight.RemoteWorldWire.*;

/** One remote namespace, bounded reassembly, immutable chunk revisions and expiry.
 * ACK certifies data receipt only, never a rendered frame or physical publication. */
public final class RemoteWorldCache {
    private record Key(int x,int z){}
    private record Ready(long revision,RemoteChunkData chunk,long seen){}
    private static final class Assembly {
        final long revision,started;final byte[] digest;final byte[][] parts;int bytes;
        Assembly(Packet p,long now){revision=p.revision();started=now;digest=p.digest();parts=new byte[p.parts()][];}
    }
    private final LongSupplier clock;
    private final Map<Key,Ready> chunks=new HashMap<>();
    private final Map<Key,Long> latest=new HashMap<>();
    private final Map<Key,Long> unavailable=new HashMap<>();
    private final Map<Key,Assembly> pending=new HashMap<>();
    private Scope scope;private long scopeSeen;private long highEpoch;private int peakCache,peakStaging;
    public RemoteWorldCache(LongSupplier clock){this.clock=Objects.requireNonNull(clock);}
    public Optional<Scope> scope(){expire();return Optional.ofNullable(scope);}
    public int chunkCount(){expire();return chunks.size();}
    public int cacheBytes(){return chunks.values().stream().mapToInt(r->r.chunk.residentBytes()).sum();}
    public int stagingBytes(){return pending.values().stream().mapToInt(a->a.bytes).sum();}
    public int peakCacheBytes(){return peakCache;}public int peakStagingBytes(){return peakStaging;}
    public void disconnect(){clear();highEpoch=0;}
    public void clear(){scope=null;chunks.clear();latest.clear();unavailable.clear();pending.clear();}
    public void expire(){
        long now=clock.getAsLong();
        if(scope!=null && (now<scopeSeen||now-scopeSeen>TTL_NANOS)){clear();return;}
        chunks.values().removeIf(r->now<r.seen||now-r.seen>TTL_NANOS);
        pending.values().removeIf(a->now<a.started||now-a.started>2_000_000_000L);
    }
    public Optional<RemoteChunkData> chunk(String dimension,int x,int z){
        expire();if(scope==null||!scope.destinationDimension().equals(dimension))return Optional.empty();
        var ready=chunks.get(new Key(x,z));return ready==null?Optional.empty():Optional.of(ready.chunk);
    }
    public List<RemoteChunkData> chunks(){expire();return chunks.values().stream().map(r->r.chunk).toList();}
    public boolean accept(Packet p,UUID actualOwner,String actualSourceDimension,Consumer<RemoteChunkData> validate,
                          Consumer<Packet> ack)throws IOException{
        try{return acceptChecked(p,actualOwner,actualSourceDimension,validate,ack);}
        catch(IOException|RuntimeException e){clear();throw new IOException("remote cache refused data",e);}
    }
    private boolean acceptChecked(Packet p,UUID actualOwner,String actualSourceDimension,Consumer<RemoteChunkData> validate,
                                  Consumer<Packet> ack)throws IOException{
        expire();long now=clock.getAsLong();
        if(p.kind()==Kind.OPEN){
            Scope proposed=p.scope();
            if(!proposed.owner().equals(actualOwner)||!proposed.sourceDimension().equals(actualSourceDimension))return false;
            if(scope!=null&&scope.equals(proposed)){scopeSeen=now;ack.accept(control(Kind.ACK,scope,scope.centerX(),scope.centerZ(),0));return true;}
            if(p.epoch()<=highEpoch)return false;
            clear();scope=proposed;highEpoch=p.epoch();scopeSeen=now;
            ack.accept(control(Kind.ACK,scope,scope.centerX(),scope.centerZ(),0));return true;
        }
        if(scope==null||!scope.owner().equals(actualOwner)||!scope.sourceDimension().equals(actualSourceDimension)||
           !scope.session().equals(p.session())||scope.epoch()!=p.epoch())return false;
        if(p.kind()==Kind.CLOSE){clear();return true;}
        if(!scope.contains(p.x(),p.z()))return false;
        Key key=new Key(p.x(),p.z());long floor=latest.getOrDefault(key,0L);
        if(p.revision()<floor)return false;
        if(p.kind()==Kind.UNAVAILABLE){chunks.remove(key);pending.remove(key);latest.put(key,p.revision());unavailable.put(key,p.revision());scopeSeen=now;return true;}
        if(p.kind()==Kind.INVALIDATE){
            if(p.revision()>floor){chunks.remove(key);pending.remove(key);latest.put(key,p.revision());}
            return true;
        }
        if(p.kind()==Kind.HEARTBEAT){
            scopeSeen=now;Ready r=chunks.get(key);
            if(r!=null&&r.revision==p.revision())chunks.put(key,new Ready(r.revision,r.chunk,now));
            else if(r!=null){chunks.remove(key);latest.put(key,p.revision());}
            if(r!=null&&r.revision==p.revision())ack.accept(control(Kind.ACK,scope,p.x(),p.z(),p.revision()));
            return true;
        }
        if(p.kind()!=Kind.CHUNK)return false;
        if(p.revision()<=unavailable.getOrDefault(key,-1L))return false;
        Ready ready=chunks.get(key);
        if(ready!=null&&ready.revision==p.revision()){ack.accept(control(Kind.ACK,scope,p.x(),p.z(),p.revision()));return true;}
        if(p.revision()>floor){chunks.remove(key);pending.remove(key);latest.put(key,p.revision());}
        Assembly a=pending.get(key);
        if(a==null){
            if(pending.size()>=2)throw new IOException("reassembly slot budget");
            a=new Assembly(p,now);pending.put(key,a);
        }
        if(a.revision!=p.revision()||a.parts.length!=p.parts()||!Arrays.equals(a.digest,p.digest()))throw new IOException("conflicting fragments");
        byte[] part=p.bytes();
        if(a.parts[p.index()]!=null){if(!Arrays.equals(a.parts[p.index()],part))throw new IOException("conflicting duplicate");return true;}
        if(a.bytes+part.length>RemoteChunkData.MAX_COMPRESSED||stagingBytes()+part.length>STAGING_BYTES)throw new IOException("staging budget");
        a.parts[p.index()]=part;a.bytes+=part.length;peakStaging=Math.max(peakStaging,stagingBytes());
        for(var b:a.parts)if(b==null)return true;
        var assembled=new ByteArrayOutputStream(a.bytes);for(var b:a.parts)assembled.write(b);
        byte[] compressed=assembled.toByteArray();
        if(!Arrays.equals(hash(compressed),a.digest))throw new IOException("chunk integrity");
        RemoteChunkData decoded=RemoteChunkData.decodeRaw(RemoteChunkData.inflate(compressed));
        if(decoded.x()!=p.x()||decoded.z()!=p.z()||decoded.minY()!=scope.minY()||decoded.sections()!=scope.sections())throw new IOException("chunk/scope identity");
        validate.accept(decoded);
        if(chunks.size()>=MAX_CHUNKS&&!chunks.containsKey(key)||cacheBytes()+decoded.residentBytes()>CACHE_BYTES)throw new IOException("cache budget");
        pending.remove(key);chunks.put(key,new Ready(p.revision(),decoded,now));scopeSeen=now;
        peakCache=Math.max(peakCache,cacheBytes());ack.accept(control(Kind.ACK,scope,p.x(),p.z(),p.revision()));return true;
    }
}
