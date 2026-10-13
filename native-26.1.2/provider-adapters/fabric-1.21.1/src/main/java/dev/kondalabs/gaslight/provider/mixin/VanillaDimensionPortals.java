package dev.kondalabs.gaslight.provider.mixin;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.platform_specific.IPConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=IPConfig.class,remap=false)
public class VanillaDimensionPortals {
    @Inject(method="onConfigChanged",at=@At("TAIL"))
    private void gaslight$vanilla(CallbackInfo ci){IPGlobal.netherPortalMode=IPGlobal.NetherPortalMode.vanilla;IPGlobal.endPortalMode=IPGlobal.EndPortalMode.vanilla;}
}
