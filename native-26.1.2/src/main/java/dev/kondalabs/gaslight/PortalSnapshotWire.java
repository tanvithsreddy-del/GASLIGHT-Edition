package dev.kondalabs.gaslight;

import dev.kondalabs.gaslight.NativePortal.*;
import java.io.*;
import java.util.*;
import java.util.function.LongSupplier;
import java.util.zip.*;

/** Strict, self-contained clientbound format for an experimental voxel preview.
 * Not a vanilla chunk stream; intentionally bounded and fail-closed. The server owns
 * every coordinate and every voxel. No client-to-server requests are accepted.
 */
public final class PortalSnapshotWire {
    private PortalSnapshotWire() {}
    public static final int MAGIC=0x474C5032; // GLP2
    public static final int VERSION=2, MAX_BYTES=196_608, MAX_VOXELS=PortalPreviewCore.MAX_VOXELS;
    public record Message(UUID owner,long generation,boolean retired,PortalPreviewCore.Published snapshot) {
        public Message {
            Objects.requireNonNull(owner);
            if(generation<0 || retired==(snapshot!=null))throw new IllegalArgumentException("invalid lifecycle envelope");
            if(snapshot!=null && (!snapshot.owner().equals(owner)||snapshot.generation()!=generation))
                throw new IllegalArgumentException("mismatched snapshot owner/epoch");
        }
        public static Message snapshot(PortalPreviewCore.Published p){return new Message(p.owner(),p.generation(),false,p);}
        public static Message retire(UUID owner,long generation){return new Message(owner,generation,true,null);}
    }
    private static void vec(DataOutputStream out,V v)throws IOException {
        out.writeDouble(v.x());out.writeDouble(v.y());out.writeDouble(v.z());
    }
    private static V vec(DataInputStream in)throws IOException {
        V v=new V(in.readDouble(),in.readDouble(),in.readDouble());
        if(!Double.isFinite(v.x())||!Double.isFinite(v.y())||!Double.isFinite(v.z())||
           Math.abs(v.x())>30_000_000||Math.abs(v.z())>30_000_000||Math.abs(v.y())>4096)
            throw new IOException("non-finite/out-of-bounds portal coordinate");
        return v;
    }
    private static void surface(DataOutputStream out,Surface s)throws IOException {
        vec(out,s.centre());vec(out,s.tangent());out.writeDouble(s.width());out.writeDouble(s.height());
    }
    private static Surface surface(DataInputStream in)throws IOException {
        V c=vec(in),t=vec(in);double w=in.readDouble(),h=in.readDouble();
        try {return new Surface(c,t,w,h);}catch(RuntimeException e){throw new IOException("invalid surface",e);}
    }
    public static byte[] encode(Message msg) throws IOException {
        Objects.requireNonNull(msg);
        ByteArrayOutputStream buf=new ByteArrayOutputStream(4096);
        DataOutputStream out=new DataOutputStream(buf);
        out.writeInt(MAGIC);out.writeByte(VERSION);out.writeByte(msg.retired()?2:1);
        out.writeLong(msg.owner().getMostSignificantBits());out.writeLong(msg.owner().getLeastSignificantBits());
        out.writeLong(msg.generation());
        if(!msg.retired()){
            PortalPreviewCore.Published p=msg.snapshot();
            out.writeLong(p.tick());
            surface(out,p.passage().entrance());surface(out,p.passage().exit());
            PortalPreviewCore.Scene s=p.scene();PortalPreviewCore.Box b=s.box();
            out.writeInt(b.ox());out.writeInt(b.oy());out.writeInt(b.oz());
            out.writeInt(b.sx());out.writeInt(b.sy());out.writeInt(b.sz());
            int[] voxels=s.voxels();
            if(voxels.length>MAX_VOXELS)throw new IOException("voxel limit");
            ByteArrayOutputStream raw=new ByteArrayOutputStream(voxels.length*4);
            DataOutputStream din=new DataOutputStream(raw);
            for(int code:voxels){if(code==PortalPreviewCore.UNKNOWN)throw new IOException("unknown voxel");din.writeInt(code);}
            byte[] rawBytes=raw.toByteArray();
            CRC32 crc=new CRC32();crc.update(rawBytes);
            ByteArrayOutputStream comp=new ByteArrayOutputStream();
            try(DeflaterOutputStream zip=new DeflaterOutputStream(comp,new Deflater(Deflater.BEST_SPEED))){zip.write(rawBytes);}
            byte[] packed=comp.toByteArray();
            out.writeInt(voxels.length);out.writeInt((int)crc.getValue());out.writeInt(packed.length);
            out.write(packed);
        }
        out.flush();
        if(buf.size()>MAX_BYTES)throw new IOException("packet budget exceeded");
        return buf.toByteArray();
    }
    public static Message decode(byte[] data) throws IOException {
        if(data==null||data.length<30||data.length>MAX_BYTES)throw new IOException("invalid wire size");
        DataInputStream in=new DataInputStream(new ByteArrayInputStream(data));
        if(in.readInt()!=MAGIC||in.readUnsignedByte()!=VERSION)throw new IOException("unknown preview format");
        int kind=in.readUnsignedByte();if(kind!=1&&kind!=2)throw new IOException("unknown lifecycle opcode");
        UUID owner=new UUID(in.readLong(),in.readLong());long generation=in.readLong();
        if(generation<0)throw new IOException("negative generation");
        if(kind==2){if(in.available()!=0)throw new IOException("retirement trailing bytes");return Message.retire(owner,generation);}
        long tick=in.readLong();if(tick<0)throw new IOException("negative world tick");
        Surface a=surface(in),b=surface(in);
        PortalPreviewCore.Box box;
        try{box=new PortalPreviewCore.Box(in.readInt(),in.readInt(),in.readInt(),
                 in.readInt(),in.readInt(),in.readInt());}
        catch(RuntimeException e){throw new IOException("invalid snapshot bounds",e);}
        int count=in.readInt(),crcRead=in.readInt(),packedLength=in.readInt();
        if(count!=box.size()||count>MAX_VOXELS||packedLength<1||packedLength>MAX_BYTES||packedLength!=in.available())
            throw new IOException("incorrect snapshot length");
        byte[] packed=new byte[packedLength];in.readFully(packed);
        int rawLength=count*4;
        byte[] raw;
        try(InflaterInputStream unzip=new InflaterInputStream(new ByteArrayInputStream(packed))){
            raw=unzip.readNBytes(rawLength+1);
            if(raw.length!=rawLength||unzip.read()!=-1)throw new IOException("decompression length mismatch");
        }catch(ZipException e){throw new IOException("invalid compressed voxel payload",e);}
        CRC32 crc=new CRC32();crc.update(raw);
        if((int)crc.getValue()!=crcRead)throw new IOException("voxel checksum mismatch");
        int[] voxels=new int[count];DataInputStream input=new DataInputStream(new ByteArrayInputStream(raw));
        for(int i=0;i<count;i++){
            int code=input.readInt();if(code==PortalPreviewCore.UNKNOWN)throw new IOException("unknown voxel in payload");
            voxels[i]=code;
        }
        Passage portal;
        try{portal=new Passage(a,b,owner,generation);}catch(RuntimeException e){throw new IOException("invalid portal pair",e);}
        PortalPreviewCore.Published p=new PortalPreviewCore.Published(owner,generation,portal,
                new PortalPreviewCore.Scene(box,voxels),tick);
        return Message.snapshot(p);
    }
    /** Client-only cache: generation-monotonic and explicitly cleared on server disconnect.
     * Every access requires current player ID; snapshots expire unless refreshed.
     */
    public static final class Inbox {
        private final LongSupplier clock;
        private final long maxAgeNanos;
        private PortalPreviewCore.Published current;
        private UUID owner;
        private long latest=-1,updated=0;
        private boolean retiredEpoch=false;
        public Inbox(LongSupplier clock,long maxAgeNanos){
            this.clock=Objects.requireNonNull(clock);
            if(maxAgeNanos<1)throw new IllegalArgumentException("expiry");
            this.maxAgeNanos=maxAgeNanos;
        }
        public synchronized boolean accept(Message m,UUID connectedPlayer) {
            if(!m.owner().equals(connectedPlayer))return false;
            if(owner!=null&&!owner.equals(connectedPlayer))reset();
            if(m.generation()<latest)return false;
            if(m.generation()==latest && retiredEpoch && !m.retired())return false;
            if(m.generation()>latest)retiredEpoch=false;
            owner=connectedPlayer;latest=m.generation();
            retiredEpoch=m.retired();
            current=m.retired()?null:m.snapshot();updated=clock.getAsLong();return true;
        }
        public synchronized Optional<PortalPreviewCore.Published> get(UUID player) {
            if(current==null||!Objects.equals(owner,player))return Optional.empty();
            long age=clock.getAsLong()-updated;
            if(age<0||age>maxAgeNanos){current=null;return Optional.empty();}
            return Optional.of(current);
        }
        public synchronized void reset(){current=null;owner=null;latest=-1;updated=0;retiredEpoch=false;}
    }
}
