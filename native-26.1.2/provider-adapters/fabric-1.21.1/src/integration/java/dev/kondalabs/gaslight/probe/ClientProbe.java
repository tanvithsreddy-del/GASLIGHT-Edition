package dev.kondalabs.gaslight.probe;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.BlockHitResult;
import java.nio.file.*;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.block_manipulation.BlockManipulationClient;

public final class ClientProbe implements ClientModInitializer {
    private String last="";private int remaining;private String pending;
    private static void output(String s){System.out.println("GASLIGHT_A_CLIENT "+s);}
    private static String availableBlock(Minecraft mc,BlockPos p){
        var chunk=mc.level.getChunkSource().getChunk(Math.floorDiv(p.getX(),16),Math.floorDiv(p.getZ(),16),net.minecraft.world.level.chunk.status.ChunkStatus.FULL,false);
        return chunk==null?"UNAVAILABLE":chunk.getBlockState(p).toString();
    }
    private void release(Minecraft mc){mc.options.keyUp.setDown(false);mc.options.keyAttack.setDown(false);mc.options.keyUse.setDown(false);mc.options.keyJump.setDown(false);}
    @Override public void onInitializeClient(){
        if(!Boolean.getBoolean("gaslight.phaseA.probe"))throw new IllegalStateException("probe must be explicitly enabled");
        ClientTickEvents.END_CLIENT_TICK.register(mc->{try{
            if(remaining>0&&--remaining==0){release(mc);output(pending+" DONE position="+(mc.player==null?null:mc.player.position()));}
            Path p=mc.gameDirectory.toPath().resolve("probe-client.txt");if(!Files.exists(p))return;
            String cmd=Files.readString(p).trim();if(cmd.isEmpty()||cmd.equals(last))return;last=cmd;
            if(cmd.startsWith("snapshot")||cmd.startsWith("view")){
                if(mc.level==null||mc.player==null){output(cmd+" NO_WORLD");return;}
                output(cmd+" position="+mc.player.position()+" dimension="+mc.level.dimension().location()+" remote="+availableBlock(mc,new BlockPos(1024,82,1021))+" placed="+availableBlock(mc,new BlockPos(1024,82,1022))+" light="+mc.level.getBrightness(LightLayer.BLOCK,new BlockPos(1024,81,1024))+" hit="+(mc.hitResult instanceof BlockHitResult b?b.getBlockPos():mc.hitResult)+" remoteHit="+(BlockManipulationClient.remoteHitResult instanceof BlockHitResult r?r.getBlockPos():BlockManipulationClient.remoteHitResult)+" worlds="+ClientWorldLoader.getClientWorlds().size()+" flying="+mc.player.getAbilities().flying+" fps="+mc.getFps()+" heapUsed="+(Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory()));
                if(cmd.startsWith("view"))Screenshot.grab(mc.gameDirectory,mc.getMainRenderTarget(),c->output(cmd+" SCREENSHOT "+c.getString()));
            }else if(cmd.startsWith("mine")||cmd.startsWith("place")||cmd.startsWith("walk")||cmd.startsWith("fly")){
                if(mc.player==null)throw new IllegalStateException("no player");release(mc);
                if(cmd.startsWith("mine")){KeyMapping.click(InputConstants.Type.MOUSE.getOrCreate(0));mc.options.keyAttack.setDown(true);}
                if(cmd.startsWith("place"))mc.options.keyUse.setDown(true);
                if(cmd.startsWith("walk"))mc.options.keyUp.setDown(true);
                if(cmd.startsWith("fly")){mc.player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);mc.player.getAbilities().flying=true;mc.player.onUpdateAbilities();mc.options.keyUp.setDown(true);}
                String[] parts=cmd.split(" ");remaining=parts.length>1?Integer.parseInt(parts[1]):cmd.startsWith("walk")?35:cmd.startsWith("fly")?28:5;
                if(remaining<1||remaining>100){release(mc);throw new IllegalArgumentException("bounded input duration");}pending=cmd;
            }else if(cmd.startsWith("look")){String[] parts=cmd.split(" ");mc.player.setYRot(Float.parseFloat(parts[1]));mc.player.setXRot(Float.parseFloat(parts[2]));output(cmd+" DONE");}
            else if(cmd.startsWith("resume")){mc.options.pauseOnLostFocus=false;mc.setScreen(null);output(cmd+" DONE");}
            else if(cmd.startsWith("perspective")){mc.options.setCameraType(mc.options.getCameraType().cycle());output(cmd+" "+mc.options.getCameraType());}
            else if(cmd.startsWith("camera")){
                var frame=dev.kondalabs.gaslight.provider.CameraObserver.latest();
                if(frame==null)throw new IllegalStateException("actual rendered camera unavailable");
                output(cmd+" camera="+frame.x()+","+frame.y()+","+frame.z()+" perspective="+mc.options.getCameraType()+" portalExcluded="+frame.excludes(-1.5,80.5,.4,2.5,84.5,.6));
            }
            else if(cmd.startsWith("disconnect")){release(mc);mc.level.disconnect();mc.disconnect();mc.setScreen(new TitleScreen());output(cmd+" DONE initialized="+ClientWorldLoader.getIsInitialized());}
            else if(cmd.startsWith("connect")){var data=new ServerData("Phase A disposable","127.0.0.1:25588",ServerData.Type.OTHER);ConnectScreen.startConnecting(new TitleScreen(),mc,ServerAddress.parseString(data.ip),data,false,null);output(cmd+" REQUESTED");}
            else if(cmd.startsWith("quit")){output("QUIT");mc.stop();}
            else throw new IllegalArgumentException(cmd);
        }catch(Throwable e){e.printStackTrace();output("FAIL "+e);}});
    }
}
