package dev.kondalabs.gaslight;

import java.io.*;
import java.util.*;
import java.util.zip.*;

/** Immutable, dimension-qualified destination data. No Level or world mutation API. */
public final class RemoteChunkData {
    public static final int MAX_SECTIONS=32, MAX_RAW=1_048_576, MAX_COMPRESSED=MAX_RAW;
    public static final int MAX_BLOCK_ENTITIES=64, MAX_TAG=16_384, MAX_TAG_TOTAL=131_072;
    public record BlockEntityData(int x,int y,int z,String type,byte[] tag) {
        public BlockEntityData {
            RemoteWorldWire.identifier(type);
            if(tag==null || tag.length>MAX_TAG)throw new IllegalArgumentException("block entity tag budget");
            tag=tag.clone();
        }
        @Override public byte[] tag(){return tag.clone();}
    }
    private final int x,z,minY,sections;
    private final int[] states,biomes;
    private final byte[][] sky,block;
    private final List<BlockEntityData> blockEntities;
    private final Map<String,long[]> heights;
    public RemoteChunkData(int x,int z,int minY,int sections,int[] states,int[] biomes,
                           byte[][] sky,byte[][] block,List<BlockEntityData> entities,Map<String,long[]> heights){
        RemoteWorldWire.coordinate(x,z);
        if(minY%16!=0 || Math.abs((long)minY)>2048 || sections<1 || sections>MAX_SECTIONS ||
           states.length!=sections*4096 || biomes.length!=sections*64 || entities.size()>MAX_BLOCK_ENTITIES)
            throw new IllegalArgumentException("chunk shape budget");
        this.x=x;this.z=z;this.minY=minY;this.sections=sections;
        this.states=states.clone();this.biomes=biomes.clone();
        for(int v:states)if(v<0)throw new IllegalArgumentException("negative state id");
        for(int v:biomes)if(v<0)throw new IllegalArgumentException("negative biome id");
        this.sky=copyLight(sky,sections+2);this.block=copyLight(block,sections+2);
        int total=0;Set<String> positions=new HashSet<>();
        for(var be:entities){
            if(Math.floorDiv(be.x(),16)!=x || Math.floorDiv(be.z(),16)!=z || be.y()<minY || be.y()>=minY+sections*16 ||
               !positions.add(be.x()+","+be.y()+","+be.z()))throw new IllegalArgumentException("foreign/duplicate block entity");
            total=Math.addExact(total,be.tag.length);
        }
        if(total>MAX_TAG_TOTAL || heights.size()>8)throw new IllegalArgumentException("metadata budget");
        this.blockEntities=List.copyOf(entities);var maps=new TreeMap<String,long[]>();
        for(var h:heights.entrySet()){
            if(!h.getKey().matches("[A-Z_]{1,48}") || h.getValue().length>64)throw new IllegalArgumentException("heightmap budget");
            maps.put(h.getKey(),h.getValue().clone());
        }
        this.heights=Collections.unmodifiableMap(maps);
        if(residentBytes()>MAX_RAW)throw new IllegalArgumentException("resident chunk budget");
    }
    private static byte[][] copyLight(byte[][] a,int n){
        if(a.length!=n)throw new IllegalArgumentException("light section count");
        byte[][] result=new byte[n][];
        for(int i=0;i<n;i++){if(a[i]==null || a[i].length!=2048)throw new IllegalArgumentException("light layer budget");result[i]=a[i].clone();}
        return result;
    }
    public int x(){return x;} public int z(){return z;} public int minY(){return minY;} public int sections(){return sections;}
    public boolean contains(int bx,int by,int bz){return Math.floorDiv(bx,16)==x && Math.floorDiv(bz,16)==z && by>=minY && by<minY+16*sections;}
    public int stateId(int bx,int by,int bz){
        if(!contains(bx,by,bz))throw new IllegalArgumentException("outside remote chunk");
        return states[(by-minY)*256+(bz&15)*16+(bx&15)];
    }
    public int biomeId(int bx,int by,int bz){
        if(!contains(bx,by,bz))throw new IllegalArgumentException("outside remote chunk");
        return biomes[((by-minY)>>2)*16+((bz&15)>>2)*4+((bx&15)>>2)];
    }
    public int light(boolean skylight,int bx,int by,int bz){
        if(!contains(bx,by,bz))throw new IllegalArgumentException("outside remote chunk");
        int cell=(by&15)*256+(bz&15)*16+(bx&15);
        return (((skylight?sky:block)[((by-minY)>>4)+1][cell>>1]&255)>>(4*(cell&1)))&15;
    }
    public List<BlockEntityData> blockEntities(){return blockEntities;}
    public Map<String,long[]> heightmaps(){var r=new TreeMap<String,long[]>();heights.forEach((k,v)->r.put(k,v.clone()));return r;}
    /** Exact primitive/NBT payload storage plus conservative fixed object bookkeeping. */
    public int residentBytes(){
        int n=4096+states.length*4+biomes.length*4+(sections+2)*4096;
        for(var b:blockEntities)n+=256+b.type.length()*2+b.tag.length;
        for(var h:heights.entrySet())n+=128+h.getValue().length*8;
        return n;
    }
    public byte[] raw() throws IOException {
        var bytes=new ByteArrayOutputStream();var o=new DataOutputStream(bytes);
        o.writeInt(0x47524331);o.writeInt(x);o.writeInt(z);o.writeInt(minY);o.writeInt(sections);
        for(int v:states)o.writeInt(v);for(int v:biomes)o.writeInt(v);
        for(byte[] v:sky)o.write(v);for(byte[] v:block)o.write(v);
        o.writeInt(blockEntities.size());
        for(var b:blockEntities){o.writeInt(b.x);o.writeInt(b.y);o.writeInt(b.z);o.writeUTF(b.type);o.writeInt(b.tag.length);o.write(b.tag);}
        o.writeInt(heights.size());for(var h:heights.entrySet()){o.writeUTF(h.getKey());o.writeInt(h.getValue().length);for(long v:h.getValue())o.writeLong(v);}
        o.flush();if(bytes.size()>MAX_RAW)throw new IOException("raw chunk budget");return bytes.toByteArray();
    }
    public static RemoteChunkData decodeRaw(byte[] raw) throws IOException {
        if(raw.length>MAX_RAW)throw new IOException("raw chunk budget");
        var i=new DataInputStream(new ByteArrayInputStream(raw));
        try{
            if(i.readInt()!=0x47524331)throw new IOException("chunk version");
            int x=i.readInt(),z=i.readInt(),y=i.readInt(),n=i.readInt();
            RemoteWorldWire.coordinate(x,z);
            if(n<1||n>MAX_SECTIONS||y%16!=0||Math.abs((long)y)>2048)throw new IOException("chunk height budget");
            if(i.available()<n*4096*4+n*64*4+(n+2)*4096+8)throw new IOException("truncated chunk");
            int[] states=new int[n*4096],biomes=new int[n*64];
            for(int k=0;k<states.length;k++)states[k]=i.readInt();for(int k=0;k<biomes.length;k++)biomes[k]=i.readInt();
            byte[][] sky=new byte[n+2][2048],block=new byte[n+2][2048];
            for(var a:sky)i.readFully(a);for(var a:block)i.readFully(a);
            int count=i.readInt(),total=0;if(count<0||count>MAX_BLOCK_ENTITIES)throw new IOException("block entity count budget");
            var entities=new ArrayList<BlockEntityData>();
            for(int k=0;k<count;k++){
                int bx=i.readInt(),by=i.readInt(),bz=i.readInt();String type=RemoteWorldWire.readIdentifier(i);
                int size=i.readInt();total=Math.addExact(total,size);
                if(size<0||size>MAX_TAG||size>i.available()||total>MAX_TAG_TOTAL)throw new IOException("tag budget");
                entities.add(new BlockEntityData(bx,by,bz,type,i.readNBytes(size)));
            }
            int hm=i.readInt();if(hm<0||hm>8)throw new IOException("heightmap count budget");var heights=new TreeMap<String,long[]>();
            for(int k=0;k<hm;k++){
                String name=RemoteWorldWire.readBoundedUtf(i,48);int size=i.readInt();
                if(size<0||size>64||size*8>i.available()||heights.containsKey(name))throw new IOException("heightmap budget");
                long[] data=new long[size];for(int j=0;j<size;j++)data[j]=i.readLong();heights.put(name,data);
            }
            if(i.available()!=0)throw new IOException("trailing chunk bytes");
            return new RemoteChunkData(x,z,y,n,states,biomes,sky,block,entities,heights);
        }catch(IllegalArgumentException|ArithmeticException e){throw new IOException("malformed chunk",e);}
    }
    public static byte[] compress(byte[] raw) throws IOException {
        if(raw.length>MAX_RAW)throw new IOException("raw budget");var out=new ByteArrayOutputStream();
        try(var z=new DeflaterOutputStream(out)){z.write(raw);}
        if(out.size()>MAX_COMPRESSED)throw new IOException("compressed budget");return out.toByteArray();
    }
    public static byte[] inflate(byte[] compressed) throws IOException {
        if(compressed.length>MAX_COMPRESSED)throw new IOException("compressed budget");
        Inflater inflater=new Inflater();var out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];
        try{
            inflater.setInput(compressed);
            while(!inflater.finished()){
                int n=inflater.inflate(buffer);
                if(n==0)throw new IOException("partial/dictionary compressed chunk");
                if(out.size()+n>MAX_RAW)throw new IOException("decompression budget");out.write(buffer,0,n);
            }
            if(inflater.getRemaining()!=0)throw new IOException("trailing compressed bytes");return out.toByteArray();
        }catch(DataFormatException e){throw new IOException("invalid compression",e);}finally{inflater.end();}
    }
}
