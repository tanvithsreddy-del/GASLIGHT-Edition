package dev.kondalabs.gaslight.provider.mixin;
import dev.kondalabs.gaslight.provider.AutomaticClient;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(LocalPlayer.class)
public class SelectionMovementFence {
    @Inject(method="aiStep",at=@At("HEAD"),cancellable=true)
    private void gaslight$hold(CallbackInfo ci){if(AutomaticClient.selecting())ci.cancel();}
}
