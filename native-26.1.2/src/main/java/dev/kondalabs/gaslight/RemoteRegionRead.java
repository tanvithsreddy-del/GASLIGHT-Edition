package dev.kondalabs.gaslight;

import java.io.*;
import java.nio.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.storage.*;

/** Read-only Anvil input; native compression, NBT accounting and palette codecs.
 * Native RegionFileStorage.read creates absent region files, so it is deliberately not used here. */
public final class RemoteRegionRead {
    public static final int NBT_BYTES=4*1_048_576,COMPRESSED_BYTES=2*1_048_576;
    private RemoteRegionRead(){}
    private static void interrupted()throws IOException{if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("acquisition cancelled");}
    private static void read(FileChannel channel,ByteBuffer bytes,long offset)throws IOException{
        while(bytes.hasRemaining()){interrupted();int n=channel.read(bytes,offset);if(n<=0)throw new EOFException("partial region record");offset+=n;}bytes.flip();
    }
    public static Optional<CompoundTag> readTag(Path region,ChunkPos pos)throws IOException{
        interrupted();Path file=region.resolve("r."+Math.floorDiv(pos.x(),32)+"."+Math.floorDiv(pos.z(),32)+".mca");
        if(!Files.exists(file))return Optional.empty();
        try(FileChannel channel=FileChannel.open(file,StandardOpenOption.READ)){
            long length=channel.size();var modified=Files.getLastModifiedTime(file);if(length<8192)throw new IOException("partial region header");
            long index=4L*(Math.floorMod(pos.x(),32)+32*Math.floorMod(pos.z(),32));
            var header=ByteBuffer.allocate(4);read(channel,header,index);int location=header.getInt();if(location==0)return Optional.empty();
            int sector=location>>>8,count=location&255;
            if(sector<2||count==0||(long)sector*4096+(long)count*4096>length)throw new IOException("region sector outside file");
            var meta=ByteBuffer.allocate(5);read(channel,meta,(long)sector*4096);int size=meta.getInt(),compression=Byte.toUnsignedInt(meta.get());
            if(size<1||size>count*4096-4||size-1>COMPRESSED_BYTES)throw new IOException("compressed region record budget/length");
            RegionFileVersion version=RegionFileVersion.fromId(compression&127);if(version==null)throw new IOException("unknown region compression");
            byte[] payload;
            if((compression&128)!=0){
                if(size!=1)throw new IOException("invalid external region record");
                Path external=region.resolve("c."+pos.x()+"."+pos.z()+".mcc");
                try(FileChannel ext=FileChannel.open(external,StandardOpenOption.READ)){
                    long n=ext.size();if(n<1||n>COMPRESSED_BYTES)throw new IOException("external record budget");var buffer=ByteBuffer.allocate((int)n);read(ext,buffer,0);payload=buffer.array();}
            }else{var buffer=ByteBuffer.allocate(size-1);read(channel,buffer,(long)sector*4096+5);payload=buffer.array();}
            CompoundTag tag;
            try(var input=new DataInputStream(version.wrap(new ByteArrayInputStream(payload)))){tag=NbtIo.read(input,NbtAccounter.create(NBT_BYTES));}
            header.clear();read(channel,header,index);if(header.getInt()!=location||channel.size()!=length||!modified.equals(Files.getLastModifiedTime(file)))throw new RemoteChunkLeases.Retry("region changed during read");
            interrupted();if(tag==null)throw new IOException("missing chunk NBT");return Optional.of(tag);
        }
    }
    public static boolean hasStructures(CompoundTag tag){
        var structures=tag.getCompoundOrEmpty("structures");return !structures.getCompoundOrEmpty("starts").isEmpty()||!structures.getCompoundOrEmpty("References").isEmpty();
    }
    public static RemoteChunkData decode(ServerLevel level,ChunkPos pos,CompoundTag tag,long deadline)throws IOException{
        interrupted();RemoteChunkCapture.budget(deadline);
        // A cold render read is never permitted to upgrade, retrogen or repair a saved world.
        int current=net.minecraft.SharedConstants.getCurrentVersion().dataVersion().version();
        if(tag.getIntOr("DataVersion",-1)!=current)throw new IOException("saved chunk version requires native migration outside acquisition");
        var encoded=tag.get("sections");if(!(encoded instanceof ListTag list))throw new IOException("saved sections absent/malformed");
        var factory=level.palettedContainerFactory();var present=new HashSet<Integer>();
        for(var value:list){
            if(!(value instanceof CompoundTag section))throw new IOException("malformed saved section");
            int y=section.getByteOr("Y",(byte)127);if(y<level.getMinY()/16||y>=level.getMinY()/16+level.getSectionsCount())continue;
            if(!present.add(y)||!section.contains("block_states")||!section.contains("biomes"))throw new IOException("missing/duplicate render section");
            if(factory.blockStatesContainerCodec().parse(NbtOps.INSTANCE,section.getCompoundOrEmpty("block_states")).error().isPresent()||
               factory.biomeContainerCodec().parse(NbtOps.INSTANCE,section.getCompoundOrEmpty("biomes")).error().isPresent())throw new IOException("invalid saved block/biome palette");
        }
        if(present.size()!=level.getSectionsCount())throw new IOException("saved render sections incomplete; never substitute air");
        var data=SerializableChunkData.parse(level,level.palettedContainerFactory(),tag);
        if(data==null||!data.chunkPos().equals(pos)||data.chunkStatus()!=ChunkStatus.FULL||!data.lightCorrect()||data.belowZeroRetrogen()!=null)
            throw new IOException("saved chunk is not matching FULL, light-correct data");
        int n=level.getSectionsCount(),min=level.getMinY(),first=min/16-1;
        if(n<1||n>RemoteChunkData.MAX_SECTIONS)throw new IOException("saved height budget");
        var sections=new HashMap<Integer,SerializableChunkData.SectionData>();for(var s:data.sectionData())if(sections.put(s.y(),s)!=null)throw new IOException("duplicate saved section");
        int[] states=new int[n*4096],biomes=new int[n*64];var registry=level.registryAccess().lookupOrThrow(Registries.BIOME);
        for(int i=0;i<n;i++){
            interrupted();RemoteChunkCapture.budget(deadline);var saved=sections.get(min/16+i);
            if(saved==null||saved.chunkSection()==null)throw new IOException("decoded saved render section missing");
            var section=saved.chunkSection();
            for(int y=0;y<16;y++)for(int z=0;z<16;z++)for(int x=0;x<16;x++)states[i*4096+y*256+z*16+x]=Block.BLOCK_STATE_REGISTRY.getId(section.getBlockState(x,y,z));
            for(int y=0;y<4;y++)for(int z=0;z<4;z++)for(int x=0;x<4;x++)biomes[i*64+y*16+z*4+x]=registry.getId(section.getNoiseBiome(x,y,z).value());
        }
        byte[][] sky=new byte[n+2][2048],block=new byte[n+2][2048];
        for(int i=0;i<n+2;i++){
            RemoteChunkCapture.budget(deadline);var saved=sections.get(first+i);
            if(saved!=null&&saved.blockLight()!=null)block[i]=saved.blockLight().copy().getData();
            if(!level.dimensionType().hasSkyLight())continue;
            if(saved!=null&&saved.skyLight()!=null){sky[i]=saved.skyLight().copy().getData();continue;}
            // Native SkyLightSectionStorage extrapolates a missing section from the next layer's bottom plane.
            DataLayer above=null;int top=sections.keySet().stream().max(Integer::compare).orElse(first+n+1);
            for(int y=first+i+1;y<=top;y++){var s=sections.get(y);if(s!=null&&s.skyLight()!=null){above=s.skyLight();break;}}
            if(above==null){Arrays.fill(sky[i],(byte)255);continue;}
            for(int y=0;y<16;y++)for(int z=0;z<16;z++)for(int x=0;x<16;x++){int index=y*256+z*16+x;sky[i][index>>1]|=(byte)(above.get(x,0,z)<<(4*(index&1)));}
        }
        var entities=new ArrayList<RemoteChunkData.BlockEntityData>();int total=0;
        if(data.blockEntities().size()>RemoteChunkData.MAX_BLOCK_ENTITIES)throw new IOException("saved block entity count budget");
        for(var saved:data.blockEntities()){
            interrupted();RemoteChunkCapture.budget(deadline);BlockPos p=BlockEntity.getPosFromTag(pos,saved);
            if(p.getX()>>4!=pos.x()||p.getZ()>>4!=pos.z()||p.getY()<min||p.getY()>=min+n*16)throw new IOException("saved block entity outside chunk");
            int index=(p.getY()-min)*256+(p.getZ()&15)*16+(p.getX()&15);var state=Block.BLOCK_STATE_REGISTRY.byId(states[index]);
            var be=BlockEntity.loadStatic(p,state,saved,level.registryAccess());if(be==null)throw new IOException("saved block entity could not be decoded");
            // Detached BE is never attached/ticked; only vanilla client-visible update data leaves the server.
            var bytes=new ByteArrayOutputStream();NbtIo.write(be.getUpdateTag(level.registryAccess()),new DataOutputStream(new BoundedOutput(bytes,RemoteChunkData.MAX_TAG)));
            total+=bytes.size();if(total>RemoteChunkData.MAX_TAG_TOTAL)throw new IOException("saved update tag budget");
            entities.add(new RemoteChunkData.BlockEntityData(p.getX(),p.getY(),p.getZ(),BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType()).toString(),bytes.toByteArray()));
        }
        var heights=new TreeMap<String,long[]>();data.heightmaps().forEach((type,values)->heights.put(type.name(),values));
        RemoteChunkCapture.budget(deadline);return new RemoteChunkData(pos.x(),pos.z(),min,n,states,biomes,sky,block,entities,heights);
    }
    private static final class BoundedOutput extends OutputStream {
        final OutputStream out;final int limit;int count;BoundedOutput(OutputStream out,int limit){this.out=out;this.limit=limit;}
        @Override public void write(int value)throws IOException{if(++count>limit)throw new IOException("saved tag byte budget");out.write(value);}
    }
}
