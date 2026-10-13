package dev.kondalabs.gaslight.provider;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import qouteall.imm_ptl.core.portal.Portal;

/** Render selection and physical publication have separate fences. */
public final class ScenePortal extends Portal {
    public static final EntityType<ScenePortal> TYPE=Registry.register(BuiltInRegistries.ENTITY_TYPE,ResourceLocation.fromNamespaceAndPath("gaslight_provider","scene_portal"),EntityType.Builder.<ScenePortal>of(ScenePortal::new,MobCategory.MISC).sized(0,0).fireImmune().noSave().noSummon().clientTrackingRange(8).updateInterval(20).build("gaslight_provider:scene_portal"));
    public ScenePortal(EntityType<?> type,Level world){super(type,world);}
    public static void initialize(){}
    @Override public boolean shouldBeSaved(){return false;}
    @Override public boolean isVisible(){return level().isClientSide?SceneVisibility.visible(portalTag):serverActive();}
    private boolean serverActive(){var service=GaslightProvider.automatic(level().getServer());return service!=null&&service.active(portalTag);}
    @Override public boolean isTeleportable(){return (level().isClientSide?SceneVisibility.interactive(portalTag):serverActive())&&super.isTeleportable();}
    @Override public boolean isInteractable(){return (level().isClientSide?SceneVisibility.interactive(portalTag):serverActive())&&super.isInteractable();}
    @Override public boolean canTeleportEntity(Entity entity){
        return (level().isClientSide?SceneVisibility.interactive(portalTag):serverActive())&&super.canTeleportEntity(entity);
    }
}
