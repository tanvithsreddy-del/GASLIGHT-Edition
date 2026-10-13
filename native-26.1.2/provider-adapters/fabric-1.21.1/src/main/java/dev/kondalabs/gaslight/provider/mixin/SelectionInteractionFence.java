package dev.kondalabs.gaslight.provider.mixin;
import dev.kondalabs.gaslight.provider.AutomaticClient;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.InteractionResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(MultiPlayerGameMode.class)
public class SelectionInteractionFence {
    @Inject(method={"destroyBlock","startDestroyBlock","continueDestroyBlock"},at=@At("HEAD"),cancellable=true)
    private void gaslight$block(CallbackInfoReturnable<Boolean> cir){if(AutomaticClient.selecting())cir.setReturnValue(false);}
    @Inject(method={"useItemOn","useItem","interact","interactAt"},at=@At("HEAD"),cancellable=true)
    private void gaslight$interact(CallbackInfoReturnable<InteractionResult> cir){if(AutomaticClient.selecting())cir.setReturnValue(InteractionResult.FAIL);}
    @Inject(method="attack",at=@At("HEAD"),cancellable=true)
    private void gaslight$attack(CallbackInfo ci){if(AutomaticClient.selecting())ci.cancel();}
}
