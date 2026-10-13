package dev.kondalabs.gaslight.provider.mixin;
import qouteall.imm_ptl.peripheral.IPOuterClientMisc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=IPOuterClientMisc.class,remap=false)
public class PortalWikiMessage {
    @Inject(method="informWithURL",at=@At("HEAD"),cancellable=true)
    private static void gaslight$suppressIntro(CallbackInfo ci){ci.cancel();}
}
