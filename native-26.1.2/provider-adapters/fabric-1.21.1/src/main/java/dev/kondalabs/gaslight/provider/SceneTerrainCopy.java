package dev.kondalabs.gaslight.provider;

import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/** Copy a surveyed block state into unused, owned private terrain. Raw section
 * copying preserves a naturally suspended block; ordinary placement would start
 * new gravity/plant callbacks. Native heightmaps, light and network dirtying still
 * run. Player actions subsequently use ordinary Minecraft block operations. */
final class SceneTerrainCopy {
    static void block(ServerLevel world,BlockPos pos,BlockState state){
        if(!world.dimension().equals(AutomaticPortalService.WORLD)||!world.getServer().isSameThread())throw new IllegalStateException("private server terrain only");
        var chunk=world.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);
        if(chunk==null||state.hasBlockEntity()||chunk.getBlockState(pos).hasBlockEntity())throw new IllegalStateException("copy preflight failed");
        var section=chunk.getSection(chunk.getSectionIndex(pos.getY()));boolean wasAir=section.hasOnlyAir();
        BlockState old=section.setBlockState(pos.getX()&15,pos.getY()&15,pos.getZ()&15,state);
        if(old==state)return;
        for(var type:new Heightmap.Types[]{Heightmap.Types.MOTION_BLOCKING,Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,Heightmap.Types.OCEAN_FLOOR,Heightmap.Types.WORLD_SURFACE})chunk.getOrCreateHeightmapUnprimed(type).update(pos.getX()&15,pos.getY(),pos.getZ()&15,state);
        chunk.setUnsaved(true);
        var light=world.getChunkSource().getLightEngine();
        if(wasAir!=section.hasOnlyAir())light.updateSectionStatus(SectionPos.of(pos),section.hasOnlyAir());
        light.checkBlock(pos);world.getChunkSource().blockChanged(pos);
    }
}
