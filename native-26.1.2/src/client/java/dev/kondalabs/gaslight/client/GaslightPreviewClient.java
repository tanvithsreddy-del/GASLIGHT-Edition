package dev.kondalabs.gaslight.client;

import dev.kondalabs.gaslight.*;
import dev.kondalabs.gaslight.NativePortal.*;
import dev.kondalabs.gaslight.RemoteViewRaycaster.Camera;
import java.util.*;
import java.io.IOException;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/** Experimental client debug preview. Uses the *integrated-server* voxel handoff.
 * It is NOT a real scene compositor: native world-depth occlusion, texture fidelity,
 * particle/lighting parity, and remote entity rendering do not yet exist.
 */
public final class GaslightPreviewClient implements ClientModInitializer {
    private static final int W=64,H=36;
    private static final PortalSnapshotWire.Inbox INBOX=new PortalSnapshotWire.Inbox(
        System::nanoTime,5_000_000_000L);
    @Override public void onInitializeClient(){
        RemoteWorldClient.register();
        ClientPlayNetworking.registerGlobalReceiver(PortalSnapshotPayload.TYPE,(payload,ctx)->{
            try {
                PortalSnapshotWire.Message decoded=PortalSnapshotWire.decode(payload.bytes());
                Minecraft client=ctx.client();
                if(client.player!=null)INBOX.accept(decoded,client.player.getUUID());
            }catch(IOException | IllegalArgumentException ignored){
                // Invalid or outdated remote data must NEVER reach the HUD.
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->INBOX.reset());
        ClientPlayConnectionEvents.JOIN.register((handler,sender,client)->INBOX.reset());
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
            Identifier.fromNamespaceAndPath("gaslight_native","phase2_preview"),GaslightPreviewClient::draw);
    }
    private static void draw(GuiGraphicsExtractor g,DeltaTracker dt){
        Minecraft mc=Minecraft.getInstance();
        if(mc.player==null||mc.level==null)return;
        var opt=RemoteWorldClient.preview();
        if(opt.isEmpty())return;
        var published=opt.get();
        // Clamp the distance to limit wasted processing when portal is not remotely visible.
        // Use the real camera, not player eye/yaw, so F5 / spectator previews
        // cannot accidentally render through the wrong source-side occluders.
        var actualCamera=mc.gameRenderer.getMainCamera();
        var cameraPosition=actualCamera.position();
        V eye=new V(cameraPosition.x,cameraPosition.y,cameraPosition.z);
        if(eye.sub(published.passage().entrance().centre()).norm()>80)return;
        double yaw=Math.toRadians(actualCamera.yRot());
        double pitch=Math.toRadians(actualCamera.xRot());
        V forward=new V(-Math.sin(yaw)*Math.cos(pitch),-Math.sin(pitch),Math.cos(yaw)*Math.cos(pitch));
        V right=new V(Math.cos(yaw),0,Math.sin(yaw));
        V up=new V(Math.sin(yaw)*Math.sin(pitch),Math.cos(pitch),-Math.cos(yaw)*Math.sin(pitch));
        int screenW=mc.getWindow().getGuiScaledWidth();
        int screenH=mc.getWindow().getGuiScaledHeight();
        double fov=Math.max(35,Math.min(110,mc.options.fov().get()));
        var cam=new Camera(eye,forward,right,up,fov,(double)screenW/screenH);
        // Experimental CPU mask: rejects unknown foreground chunks and approximates
        // solid block occlusion. Not equivalent to vanilla's real GPU depth buffer.
        PortalPreviewCore.Frame frame=PortalPreviewCore.renderOccluded(
            published.passage(),published.scene(),cam,W,H,96,(bx,by,bz)->{
                if(by<mc.level.getMinY() || by>=mc.level.getMaxY())
                    return RemoteViewRaycaster.Voxel.air();
                if(!mc.level.hasChunk(Math.floorDiv(bx,16),Math.floorDiv(bz,16)))
                    return null;
                try{
                    var state=mc.level.getBlockState(new net.minecraft.core.BlockPos(bx,by,bz));
                    return state.isAir()?RemoteViewRaycaster.Voxel.air():
                        RemoteViewRaycaster.Voxel.opaque(0);
                }catch(RuntimeException e){return null;}
            },96);
        if(frame.visiblePixels()==0)return;
        // This draws a coarse screen-aligned diagnostic aperture, not a correct depth-tested world portal.
        int width=Math.max(1,screenW/W),height=Math.max(1,screenH/H);
        int startX=(screenW-width*W)/2,startY=(screenH-height*H)/2;
        // Run length compression reduces GUI command count for broad same-color regions.
        for(int y=0;y<H;y++){
            int x=0;
            while(x<W){
                if(!frame.visibleAt(x,y)){x++;continue;}
                int color=frame.argbAt(x,y),start=x++;
                while(x<W && frame.visibleAt(x,y)&&frame.argbAt(x,y)==color)x++;
                g.fill(startX+start*width,startY+y*height,startX+x*width,startY+(y+1)*height,color);
            }
        }
    }
}
