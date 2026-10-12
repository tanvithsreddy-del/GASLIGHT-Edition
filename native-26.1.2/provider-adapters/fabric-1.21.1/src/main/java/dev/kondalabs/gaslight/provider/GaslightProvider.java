package dev.kondalabs.gaslight.provider;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import qouteall.imm_ptl.core.portal.Portal;
import java.util.IdentityHashMap;
import java.util.Map;
public final class GaslightProvider implements ModInitializer {
    public static final Logger LOG=LoggerFactory.getLogger("gaslight-provider");
    private static final Map<MinecraftServer,FixedPortalAdapter> SERVICES=new IdentityHashMap<>();
    public static FixedPortalAdapter service(MinecraftServer server){ return SERVICES.get(server); }
    @Override public void onInitialize(){
        LOG.info("GASLIGHT provider adapter initialized; automatic replacement disabled");
        LOG.info("GASLIGHT_CODE_SOURCE {}",GaslightProvider.class.getProtectionDomain().getCodeSource().getLocation());
        FixedPortalCommands.register();
        ServerLifecycleEvents.SERVER_STARTING.register(s->SERVICES.put(s,new FixedPortalAdapter(s)));
        ServerEntityEvents.ENTITY_LOAD.register((entity,world)->{
            if(entity instanceof Portal portal && FixedPortalAdapter.owned(portal)){
                var service=SERVICES.get(world.getServer());
                if(service==null||!service.permits(portal)) { portal.setTeleportable(false);portal.setIsVisible(false);portal.discard(); }
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(s->{var service=SERVICES.get(s);if(service!=null)service.tick();});
        ServerLifecycleEvents.SERVER_STOPPING.register(s->{var service=SERVICES.get(s);if(service!=null)service.shutdown();});
        ServerLifecycleEvents.SERVER_STOPPED.register(SERVICES::remove);
    }
}
