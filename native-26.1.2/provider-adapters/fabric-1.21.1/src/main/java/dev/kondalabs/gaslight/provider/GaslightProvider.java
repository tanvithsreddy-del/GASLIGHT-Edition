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
    private static final Map<MinecraftServer,AutomaticPortalService> AUTOMATIC=new IdentityHashMap<>();
    public static FixedPortalAdapter service(MinecraftServer server){ return SERVICES.get(server); }
    public static AutomaticPortalService automatic(MinecraftServer server){return AUTOMATIC.get(server);}
    @Override public void onInitialize(){
        LOG.info("GASLIGHT initialized; private-world gameplay requires /gaslight begin");
        LOG.info("GASLIGHT_CODE_SOURCE {}",GaslightProvider.class.getProtectionDomain().getCodeSource().getLocation());
        EphemeralPortal.initialize();
        ScenePortal.initialize();SceneProtocol.initialize();
        qouteall.imm_ptl.core.IPGlobal.netherPortalMode=qouteall.imm_ptl.core.IPGlobal.NetherPortalMode.vanilla;
        qouteall.imm_ptl.core.IPGlobal.endPortalMode=qouteall.imm_ptl.core.IPGlobal.EndPortalMode.vanilla;
        FixedPortalCommands.register();
        CameraTelemetry.initialize();
        ServerLifecycleEvents.SERVER_STARTING.register(s->SERVICES.put(s,new FixedPortalAdapter(s)));
        ServerLifecycleEvents.SERVER_STARTING.register(s->AUTOMATIC.put(s,new AutomaticPortalService(s)));
        ServerEntityEvents.ENTITY_LOAD.register((entity,world)->{
            if(entity instanceof Portal portal && FixedPortalAdapter.owned(portal)){
                var service=SERVICES.get(world.getServer());
                if(service==null||!service.permits(portal)) { portal.setTeleportable(false);portal.setIsVisible(false);portal.discard();LOG.info("GASLIGHT_STALE_PORTAL_REJECTED {} {}",portal.getUUID(),portal.portalTag); }
            }
            if(entity instanceof ScenePortal portal){var service=AUTOMATIC.get(world.getServer());if(service==null||!service.permits(portal)){portal.setTeleportable(false);portal.discard();}}
        });
        ServerLifecycleEvents.SERVER_STARTED.register(s->{var service=SERVICES.get(s);if(service!=null)service.recoverGuardedOwnership();});
        ServerTickEvents.END_SERVER_TICK.register(s->{var service=SERVICES.get(s);if(service!=null)service.tick();});
        ServerTickEvents.END_SERVER_TICK.register(s->{var service=AUTOMATIC.get(s);if(service!=null)service.tick();});
        ServerLifecycleEvents.SERVER_STOPPING.register(s->{var service=SERVICES.get(s);if(service!=null)service.shutdown();});
        ServerLifecycleEvents.SERVER_STOPPED.register(SERVICES::remove);
        ServerLifecycleEvents.SERVER_STOPPING.register(s->{var service=AUTOMATIC.get(s);if(service!=null)service.shutdown();});
        ServerLifecycleEvents.SERVER_STOPPED.register(AUTOMATIC::remove);
        net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register((dispatcher,registry,environment)->dispatcher.register(net.minecraft.commands.Commands.literal("gaslight")
            .then(net.minecraft.commands.Commands.literal("begin").executes(c->{var player=c.getSource().getPlayerOrException();var service=automatic(c.getSource().getServer());boolean ok=service.begin(player);c.getSource().sendSuccess(()->net.minecraft.network.chat.Component.literal(service.status()),false);return ok?1:0;}))
            .then(net.minecraft.commands.Commands.literal("status").executes(c->{c.getSource().sendSuccess(()->net.minecraft.network.chat.Component.literal(automatic(c.getSource().getServer()).status()),false);return 1;}))
            .then(net.minecraft.commands.Commands.literal("leave").executes(c->{var service=automatic(c.getSource().getServer());service.leave();return 1;}))));
    }
}
