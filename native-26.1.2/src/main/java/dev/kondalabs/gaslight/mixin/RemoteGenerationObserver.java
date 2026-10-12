package dev.kondalabs.gaslight.mixin;
import dev.kondalabs.gaslight.RemoteChunkAcquisition;
import net.minecraft.server.level.*;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.chunk.status.ChunkStep;
import net.minecraft.util.StaticCache2D;
import java.util.concurrent.CompletableFuture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** Measurement only: never cancels or replaces Minecraft generation. */
@Mixin(ChunkMap.class)
public abstract class RemoteGenerationObserver {
    @Inject(method="applyStep",at=@At("HEAD"))
    private void gaslight$observe(GenerationChunkHolder holder,ChunkStep step,StaticCache2D<GenerationChunkHolder> cache,CallbackInfoReturnable<CompletableFuture<ChunkAccess>> result){
        RemoteChunkAcquisition.observe((ChunkMap)(Object)this,holder,step);
    }
}
