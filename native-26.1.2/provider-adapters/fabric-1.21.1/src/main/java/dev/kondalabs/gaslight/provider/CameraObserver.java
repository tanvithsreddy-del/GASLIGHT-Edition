package dev.kondalabs.gaslight.provider;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;
import org.joml.Matrix4f;

/** Captures every top-level rendered camera, including F5. Telemetry is rate
 * limited to five packets/sec. It does not silently enable the publication gate. */
public final class CameraObserver implements ClientModInitializer {
    private static CameraTelemetry.Challenge challenge;
    private static long frame,lastSent;
    private static FrameClip latest;
    private static boolean reportedFailure;
    public static FrameClip latest(){return latest;}
    @Override public void onInitializeClient(){
        net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(EphemeralPortal.TYPE,qouteall.imm_ptl.core.render.PortalEntityRenderer::new);
        ClientPlayNetworking.registerGlobalReceiver(CameraTelemetry.Challenge.TYPE,(p,c)->{challenge=p;lastSent=0;});
        ClientPlayConnectionEvents.DISCONNECT.register((h,c)->{challenge=null;latest=null;frame=0;});
        WorldRenderEvents.AFTER_SETUP.register(context->{
            if(PortalRendering.isRendering())return;
            frame++;
            try{
                var position=context.camera().getPosition();float[] matrix=new float[16];
                new Matrix4f(context.projectionMatrix()).mul(context.positionMatrix()).get(matrix);
                latest=new FrameClip(position.x,position.y,position.z,matrix);
                long now=System.nanoTime();
                if(challenge!=null&&now-lastSent>=200_000_000L&&ClientPlayNetworking.canSend(CameraTelemetry.Observation.TYPE)){
                    ClientPlayNetworking.send(new CameraTelemetry.Observation(challenge.session(),challenge.generation(),frame,context.world().dimension().location().toString(),latest,true));lastSent=now;
                }
            }catch(RuntimeException unsupported){latest=null;if(!reportedFailure){reportedFailure=true;GaslightProvider.LOG.warn("GASLIGHT_CAMERA_UNAVAILABLE",unsupported);}}
        });
    }
}
