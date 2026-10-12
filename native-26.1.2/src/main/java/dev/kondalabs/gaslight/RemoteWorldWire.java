package dev.kondalabs.gaslight;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

/** Server-created session scope; fragments cannot target the ordinary client world. */
public final class RemoteWorldWire {
    public static final int MAX_CHUNKS=9, FRAGMENT=48_000, MAX_PACKET=49_152, MAX_FRAGMENTS=22;
    public static final int CACHE_BYTES=10*1_048_576, STAGING_BYTES=2*1_048_576;
    public static final long TTL_NANOS=5_000_000_000L;
    public enum Kind { OPEN, CHUNK, UNAVAILABLE, HEARTBEAT, CLOSE, ACK, RESYNC, INVALIDATE }
    public record Scope(UUID session,UUID portal,UUID owner,long epoch,String sourceDimension,String destinationDimension,
                        String dimensionType,int centerX,int centerZ,int minY,int sections,NativePortal.Passage passage){
        public Scope {
            Objects.requireNonNull(session);Objects.requireNonNull(portal);Objects.requireNonNull(owner);
            identifier(sourceDimension);identifier(destinationDimension);identifier(dimensionType);coordinate(centerX,centerZ);
            if(epoch<1||sections<1||sections>RemoteChunkData.MAX_SECTIONS||minY%16!=0||Math.abs((long)minY)>2048||passage==null||!owner.equals(passage.owner()))
                throw new IllegalArgumentException("invalid scope");
            for(var s:List.of(passage.entrance(),passage.exit())){
                var c=s.centre();var t=s.tangent();
                if(!Double.isFinite(c.x())||!Double.isFinite(c.y())||!Double.isFinite(c.z())||Math.abs(c.x())>29_500_000||Math.abs(c.z())>29_500_000||Math.abs(c.y())>2048||
                   !Double.isFinite(t.x())||!Double.isFinite(t.y())||!Double.isFinite(t.z())||s.width()>64||s.height()>512)
                    throw new IllegalArgumentException("portal geometry bounds");
            }
            if(Math.floor(passage.exit().centre().x()/16)!=centerX||Math.floor(passage.exit().centre().z()/16)!=centerZ)
                throw new IllegalArgumentException("destination identity mismatch");
        }
        public boolean contains(int x,int z){return Math.abs((long)x-centerX)<=1&&Math.abs((long)z-centerZ)<=1;}
    }
    public record Packet(Kind kind,UUID session,long epoch,int x,int z,long revision,int index,int parts,byte[] digest,byte[] bytes,Scope scope){
        public Packet {
            Objects.requireNonNull(kind);Objects.requireNonNull(session);Objects.requireNonNull(digest);Objects.requireNonNull(bytes);
            if(epoch<1 || revision<0 || digest.length!=32 || bytes.length>FRAGMENT)throw new IllegalArgumentException("packet budget");
            coordinate(x,z);
            if(kind==Kind.CHUNK && (revision<1||index<0||parts<1||parts>MAX_FRAGMENTS||index>=parts||bytes.length==0))
                throw new IllegalArgumentException("fragment shape");
            if(kind!=Kind.CHUNK && (index!=0||parts!=0||bytes.length!=0))throw new IllegalArgumentException("control shape");
            if(kind==Kind.OPEN ? scope==null || !scope.session.equals(session) || scope.epoch!=epoch : scope!=null)
                throw new IllegalArgumentException("scope shape");
            digest=digest.clone();bytes=bytes.clone();
        }
        @Override public byte[] bytes(){return bytes.clone();}@Override public byte[] digest(){return digest.clone();}
    }
    public static void identifier(String s){if(s==null||s.length()>128||!s.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))throw new IllegalArgumentException("identifier");}
    public static void coordinate(int x,int z){if(Math.abs((long)x)>1_844_000||Math.abs((long)z)>1_844_000)throw new IllegalArgumentException("coordinate bounds");}
    public static String readBoundedUtf(DataInputStream i,int max)throws IOException{
        int n=i.readUnsignedShort();if(n>max||n>i.available())throw new IOException("string budget");
        byte[] b=i.readNBytes(n);String s=new String(b,StandardCharsets.UTF_8);
        if(!Arrays.equals(b,s.getBytes(StandardCharsets.UTF_8)))throw new IOException("invalid UTF8");return s;
    }
    public static String readIdentifier(DataInputStream i)throws IOException{String s=readBoundedUtf(i,128);identifier(s);return s;}
    private static void uuid(DataOutputStream o,UUID u)throws IOException{o.writeLong(u.getMostSignificantBits());o.writeLong(u.getLeastSignificantBits());}
    private static UUID uuid(DataInputStream i)throws IOException{return new UUID(i.readLong(),i.readLong());}
    private static void surface(DataOutputStream o,NativePortal.Surface s)throws IOException{
        o.writeDouble(s.centre().x());o.writeDouble(s.centre().y());o.writeDouble(s.centre().z());
        o.writeDouble(s.tangent().x());o.writeDouble(s.tangent().y());o.writeDouble(s.tangent().z());o.writeDouble(s.width());o.writeDouble(s.height());
    }
    private static NativePortal.Surface surface(DataInputStream i)throws IOException{
        return new NativePortal.Surface(new NativePortal.V(i.readDouble(),i.readDouble(),i.readDouble()),
            new NativePortal.V(i.readDouble(),i.readDouble(),i.readDouble()),i.readDouble(),i.readDouble());
    }
    public static byte[] encode(Packet p)throws IOException{
        var b=new ByteArrayOutputStream();var o=new DataOutputStream(b);o.writeInt(0x47525731);o.writeByte(p.kind.ordinal());uuid(o,p.session);
        o.writeLong(p.epoch);o.writeInt(p.x);o.writeInt(p.z);o.writeLong(p.revision);o.writeInt(p.index);o.writeInt(p.parts);o.write(p.digest);
        o.writeInt(p.bytes.length);o.write(p.bytes);
        if(p.scope!=null){var s=p.scope;uuid(o,s.portal);uuid(o,s.owner);o.writeUTF(s.sourceDimension);o.writeUTF(s.destinationDimension);o.writeUTF(s.dimensionType);
            o.writeInt(s.centerX);o.writeInt(s.centerZ);o.writeInt(s.minY);o.writeInt(s.sections);surface(o,s.passage.entrance());surface(o,s.passage.exit());o.writeLong(s.passage.generation());}
        o.flush();if(b.size()>MAX_PACKET)throw new IOException("packet budget");return b.toByteArray();
    }
    public static Packet decode(byte[] wire)throws IOException{
        if(wire.length>MAX_PACKET)throw new IOException("packet budget");var i=new DataInputStream(new ByteArrayInputStream(wire));
        try{
            if(i.readInt()!=0x47525731)throw new IOException("wire version");int k=i.readUnsignedByte();if(k>=Kind.values().length)throw new IOException("kind");Kind kind=Kind.values()[k];
            UUID session=uuid(i);long epoch=i.readLong();int x=i.readInt(),z=i.readInt();long rev=i.readLong();int index=i.readInt(),parts=i.readInt();byte[] hash=i.readNBytes(32);
            int n=i.readInt();if(n<0||n>FRAGMENT||n>i.available())throw new IOException("fragment budget");byte[] bytes=i.readNBytes(n);Scope scope=null;
            if(kind==Kind.OPEN){UUID portal=uuid(i),owner=uuid(i);String src=readIdentifier(i),dst=readIdentifier(i),type=readIdentifier(i);int cx=i.readInt(),cz=i.readInt(),y=i.readInt(),sections=i.readInt();
                var entrance=surface(i);var exit=surface(i);long generation=i.readLong();scope=new Scope(session,portal,owner,epoch,src,dst,type,cx,cz,y,sections,new NativePortal.Passage(entrance,exit,owner,generation));}
            if(i.available()!=0)throw new IOException("trailing wire bytes");return new Packet(kind,session,epoch,x,z,rev,index,parts,hash,bytes,scope);
        }catch(IllegalArgumentException|ArithmeticException e){throw new IOException("malformed wire",e);}
    }
    public static byte[] hash(byte[] data){try{return MessageDigest.getInstance("SHA-256").digest(data);}catch(NoSuchAlgorithmException e){throw new AssertionError(e);}}
    public static Packet control(Kind k,Scope s,int x,int z,long revision){return new Packet(k,s.session,s.epoch,x,z,revision,0,0,new byte[32],new byte[0],k==Kind.OPEN?s:null);}
    public static List<Packet> fragments(Scope s,int x,int z,long revision,byte[] compressed){
        if(compressed.length==0||compressed.length>RemoteChunkData.MAX_COMPRESSED)throw new IllegalArgumentException("compressed budget");
        int count=(compressed.length+FRAGMENT-1)/FRAGMENT;byte[] digest=hash(compressed);var result=new ArrayList<Packet>();
        for(int k=0;k<count;k++)result.add(new Packet(Kind.CHUNK,s.session,s.epoch,x,z,revision,k,count,digest,Arrays.copyOfRange(compressed,k*FRAGMENT,Math.min(compressed.length,(k+1)*FRAGMENT)),null));
        return List.copyOf(result);
    }
}
