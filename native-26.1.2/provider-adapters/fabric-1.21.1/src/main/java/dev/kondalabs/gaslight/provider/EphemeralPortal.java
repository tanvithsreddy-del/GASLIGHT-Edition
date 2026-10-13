package dev.kondalabs.gaslight.provider;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import qouteall.imm_ptl.core.portal.Portal;

/** GASLIGHT owns the entity type. It is never serialized into world entity files.
 * Removing this mod cannot turn its entities into ordinary provider portals. */
public final class EphemeralPortal extends Portal {
    public static final EntityType<EphemeralPortal> TYPE=Registry.register(BuiltInRegistries.ENTITY_TYPE,
        ResourceLocation.fromNamespaceAndPath("gaslight_provider","ephemeral_portal"),
        EntityType.Builder.<EphemeralPortal>of(EphemeralPortal::new,MobCategory.MISC).sized(0,0).fireImmune().noSave().noSummon().clientTrackingRange(6).updateInterval(20).build("gaslight_provider:ephemeral_portal"));
    public EphemeralPortal(EntityType<?> type,Level world){super(type,world);}
    public static void initialize(){}
    @Override public boolean shouldBeSaved(){return false;}
}
