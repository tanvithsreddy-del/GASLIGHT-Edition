package dev.kondalabs.gaslight;

import java.io.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.LevelChunk;

/** Loaded-only read path. Never acquires tickets, generates chunks, ticks block
 * entities, writes files, or calls getChunk with create=true. */
public final class RemoteChunkCapture {
    public static RemoteChunkData capture(ServerLevel level,int cx,int cz,long deadline)throws IOException{
        // getChunkNow has a small hot cache; require a current FULL holder as well
        // so a cached object cannot keep a natively unloaded chunk renderable.
        if(!level.getChunkSource().hasChunk(cx,cz))throw new IOException("destination FULL holder unavailable");
        LevelChunk chunk=level.getChunkSource().getChunkNow(cx,cz);
        if(chunk==null)throw new IOException("loaded FULL destination chunk unavailable");
        if(!chunk.isLightCorrect())throw new IOException("destination chunk lighting not certified");
        // lightOnInColumn is an enabled-update set, not a data-readiness certificate:
        // unobserved loaded chunks can have correct light while updates are disabled.
        // Require the genuine FULL chunk's isLightCorrect flag and copy the engine's
        // actual concrete/implicit light values without enabling or mutating it.
        int minY=level.getMinY(),n=chunk.getSections().length;
        if(n<1||n>RemoteChunkData.MAX_SECTIONS)throw new IOException("dimension height budget");
        int[] states=new int[n*4096],biomes=new int[n*64];
        var biomeRegistry=level.registryAccess().lookupOrThrow(Registries.BIOME);
        for(int s=0;s<n;s++){
            budget(deadline);var section=chunk.getSections()[s];
            for(int y=0;y<16;y++)for(int z=0;z<16;z++)for(int x=0;x<16;x++)
                states[s*4096+y*256+z*16+x]=Block.BLOCK_STATE_REGISTRY.getId(section.getBlockState(x,y,z));
            for(int y=0;y<4;y++)for(int z=0;z<4;z++)for(int x=0;x<4;x++)
                biomes[s*64+y*16+z*4+x]=biomeRegistry.getId(section.getNoiseBiome(x,y,z).value());
        }
        byte[][] sky=light(level,cx,cz,minY/16-1,n+2,LightLayer.SKY,deadline);
        byte[][] block=light(level,cx,cz,minY/16-1,n+2,LightLayer.BLOCK,deadline);
        var entities=new ArrayList<RemoteChunkData.BlockEntityData>();int total=0;
        if(chunk.getBlockEntities().size()>RemoteChunkData.MAX_BLOCK_ENTITIES)throw new IOException("block entity budget");
        var sorted=new ArrayList<>(chunk.getBlockEntities().entrySet());sorted.sort(Comparator.comparingLong(v->v.getKey().asLong()));
        for(var entry:sorted){
            budget(deadline);var be=entry.getValue();var b=new ByteArrayOutputStream();
            // Update tags are exactly the vanilla client-visible/render data, not inventories or entities.
            NbtIo.write(be.getUpdateTag(level.registryAccess()),new DataOutputStream(new LimitedOutput(b,RemoteChunkData.MAX_TAG)));
            total+=b.size();if(total>RemoteChunkData.MAX_TAG_TOTAL)throw new IOException("block entity total budget");
            BlockPos p=entry.getKey();String type=BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType()).toString();
            entities.add(new RemoteChunkData.BlockEntityData(p.getX(),p.getY(),p.getZ(),type,b.toByteArray()));
        }
        var heights=new TreeMap<String,long[]>();for(var h:chunk.getHeightmaps())heights.put(h.getKey().name(),h.getValue().getRawData());
        budget(deadline);return new RemoteChunkData(cx,cz,minY,n,states,biomes,sky,block,entities,heights);
    }
    private static byte[][] light(ServerLevel level,int cx,int cz,int first,int n,LightLayer layer,long deadline)throws IOException{
        var listener=level.getChunkSource().getLightEngine().getLayerListener(layer);byte[][] data=new byte[n][2048];
        var pos=new BlockPos.MutableBlockPos();
        for(int s=0;s<n;s++){
            budget(deadline);var live=listener.getDataLayerData(SectionPos.of(cx,first+s,cz));
            if(live!=null){data[s]=live.copy().getData();continue;}
            // Use the genuine engine's implicit/extrapolated values when no concrete layer exists.
            for(int y=0;y<16;y++)for(int z=0;z<16;z++)for(int x=0;x<16;x++){
                int index=y*256+z*16+x;pos.set(cx*16+x,(first+s)*16+y,cz*16+z);
                int value=listener.getLightValue(pos);data[s][index>>1]|=(byte)((value&15)<<(4*(index&1)));
            }
        }
        return data;
    }
    public static void budget(long deadline)throws IOException{if(System.nanoTime()>deadline)throw new IOException("capture CPU deadline");}
    private static final class LimitedOutput extends OutputStream {
        private final OutputStream out;private final int max;private int count;
        LimitedOutput(OutputStream out,int max){this.out=out;this.max=max;}
        @Override public void write(int b)throws IOException{if(++count>max)throw new IOException("NBT byte budget");out.write(b);}
        @Override public void write(byte[] b,int off,int n)throws IOException{if(n>max-count)throw new IOException("NBT byte budget");count+=n;out.write(b,off,n);}
    }
}
