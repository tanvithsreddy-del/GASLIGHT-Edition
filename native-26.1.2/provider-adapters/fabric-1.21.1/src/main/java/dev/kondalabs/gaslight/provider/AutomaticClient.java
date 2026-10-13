package dev.kondalabs.gaslight.provider;

import java.util.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.client.renderer.chunk.*;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import qouteall.imm_ptl.core.ducks.IEWorldRenderer;
import qouteall.imm_ptl.core.render.ImmPtlViewArea;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;

/** Owns the frame barrier; the server never guesses a camera from player yaw. */
public final class AutomaticClient implements ClientModInitializer {
    private static SceneProtocol.Offer offer;
    private static GenerationFence fence;
    private static FrameClip camera;
    private static long frame,received,selectedAt;
    private static boolean hidden,main;
    private static int views;
    private static Vec3 heldPosition;
    private static final Map<SectionRenderDispatcher.RenderSection,SectionRenderDispatcher.CompiledSection> waiting=new IdentityHashMap<>();
    private static final Set<SectionRenderDispatcher.RenderSection> complete=Collections.newSetFromMap(new IdentityHashMap<>());
    private static final List<BlockState> boundary=new ArrayList<>();
    private static int hashCursor;
    private static boolean dataReady,hashReady;
    private static String pendingFailure;
    private static int digestRetries;
    private static long cancellingAt,progressAt;
    private static String readiness="idle";
    private static boolean serverArmed;
    private static boolean supportedProfile(){var mc=Minecraft.getInstance();var loader=net.fabricmc.loader.api.FabricLoader.getInstance();return !loader.isModLoaded("sodium")&&!loader.isModLoaded("iris")&&mc.options.renderDistance().get()<=4;}
    private static boolean playerClearOfPrevious(Minecraft mc){
        if(offer.previous()==null)return true;
        var body=mc.player.getBoundingBox().inflate(.75);
        for(Vec3 p:java.util.List.of(offer.previous(),offer.previousDestination())){
            boolean x=offer.previousXPlane();
            if(body.intersects(new net.minecraft.world.phys.AABB(p.x-(x?.1:32),offer.minY(),p.z-(x?32:.1),p.x+(x?.1:32),offer.maxY(),p.z+(x?32:.1))))return false;
        }
        return true;
    }
    public static boolean selecting(){return pendingFailure!=null||fence!=null&&fence.state()==GenerationFence.State.SELECTING;}
    public static String status(){return "offer="+(offer==null?0:offer.generation())+",fence="+(fence==null?"none":fence.state())+",readiness="+readiness+",meshes="+complete.size()+",pending="+waiting.size()+",boundary="+hashCursor+",frame="+frame+",views="+views+",hidden="+hidden+",selected="+SceneVisibility.selected+",ack="+SceneVisibility.acknowledged;}
    private static void clear(){offer=null;fence=null;camera=null;waiting.clear();complete.clear();boundary.clear();hashCursor=0;dataReady=false;hashReady=false;digestRetries=0;pendingFailure=null;cancellingAt=0;readiness="idle";serverArmed=false;SceneVisibility.clear();}
    private static void fail(String why){
        if(pendingFailure!=null)return;
        GaslightProvider.LOG.error("GASLIGHT_CLIENT_FAILED {}",why);pendingFailure=why;
        if(fence!=null)fence.fail();
        // Minecraft still accesses gameMode after WorldRenderEvents.END. Retain
        // the selected view and close on the next tick, never roll it back here.
        Minecraft.getInstance().setScreen(new DisconnectedScreen(new TitleScreen(),Component.literal("GASLIGHT closed the scene"),Component.literal(why)));
    }
    @Override public void onInitializeClient(){
        net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(ScenePortal.TYPE,qouteall.imm_ptl.core.render.PortalEntityRenderer::new);
        ClientPlayConnectionEvents.DISCONNECT.register((h,c)->clear());
        ClientPlayConnectionEvents.JOIN.register((h,s,c)->{if(ClientPlayNetworking.canSend(SceneProtocol.Caps.TYPE))ClientPlayNetworking.send(new SceneProtocol.Caps(1,supportedProfile(),c.options.renderDistance().get()));});
        ClientPlayNetworking.registerGlobalReceiver(SceneProtocol.Offer.TYPE,(p,c)->{
            if(offer!=null||selecting()){fail("overlapping scene offers");return;}
            offer=p;received=System.nanoTime();fence=new GenerationFence(p.session(),p.generation(),received+45_000_000_000L);waiting.clear();complete.clear();boundary.clear();hashCursor=0;hashReady=false;dataReady=false;
            cancellingAt=0;progressAt=received;readiness="waiting for native chunks";serverArmed=false;
            GaslightProvider.LOG.info("GASLIGHT_CLIENT_OFFER {}",p.generation());
        });
        ClientPlayNetworking.registerGlobalReceiver(SceneProtocol.Armed.TYPE,(p,c)->{if(offer!=null&&p.session().equals(offer.session())&&p.generation()==offer.generation()&&cancellingAt==0){serverArmed=true;GaslightProvider.LOG.info("GASLIGHT_CLIENT_ARMED g={}",p.generation());}});
        ClientPlayNetworking.registerGlobalReceiver(SceneProtocol.Withdraw.TYPE,(p,c)->{
            if(offer==null||!p.session().equals(offer.session())||p.generation()!=offer.generation())return;
            if(fence.state()==GenerationFence.State.SELECTING){fail("server withdrew a locally selected generation");return;}
            offer=null;fence=null;waiting.clear();complete.clear();boundary.clear();cancellingAt=0;readiness="cancelled without changing active view";
            GaslightProvider.LOG.info("GASLIGHT_CLIENT_WITHDRAWN g={} {}",p.generation(),status());
        });
        ClientPlayNetworking.registerGlobalReceiver(SceneProtocol.Ack.TYPE,(p,c)->{
            if(fence==null||offer==null||!p.session().equals(offer.session())||p.generation()!=offer.generation())return;
            if(!p.accepted()||!fence.commit(p.session(),p.generation(),p.frame(),System.nanoTime())){fail("server rejected or expired the selected generation");return;}
            SceneVisibility.acknowledged=true;GaslightProvider.LOG.info("GASLIGHT_CLIENT_COMMITTED g={} frame={} ackMs={}",p.generation(),p.frame(),(System.nanoTime()-selectedAt)/1e6);
            offer=null;fence=null;waiting.clear();complete.clear();boundary.clear();
        });
        ClientTickEvents.START_CLIENT_TICK.register(mc->{
            if(pendingFailure!=null){String why=pendingFailure;if(mc.level!=null){mc.level.disconnect();mc.disconnect();}clear();mc.setScreen(new DisconnectedScreen(new TitleScreen(),Component.literal("GASLIGHT closed the scene"),Component.literal(why)));return;}
            if(selecting()&&mc.player!=null){mc.player.setDeltaMovement(Vec3.ZERO);if(heldPosition!=null)mc.player.setPos(heldPosition);}
        });
        ClientTickEvents.END_CLIENT_TICK.register(mc->{
            if(mc.level==null||!mc.level.dimension().equals(AutomaticPortalService.WORLD)){if(selecting())fail("dimension changed during scene acknowledgement");else clear();return;}
            if((offer!=null||SceneVisibility.selected!=null)&&!supportedProfile()){fail("this gameplay profile requires the vanilla renderer and render distance 2–4");return;}
            if(offer==null)return;
            long now=System.nanoTime();
            if(selecting()&&now-selectedAt>2_000_000_000L||cancellingAt!=0&&now-cancellingAt>2_000_000_000L){fail("scene acknowledgement timed out");return;}
            boolean moved=mc.player!=null&&mc.player.position().distanceToSqr(new Vec3(offer.origin().x,mc.player.getY(),offer.origin().z))>=256;
            if((now-received>40_000_000_000L||moved)&&cancellingAt==0&&!selecting()){
                cancellingAt=now;fence.fail();ClientPlayNetworking.send(new SceneProtocol.Cancel(offer.session(),offer.generation()));
                GaslightProvider.LOG.info("GASLIGHT_CLIENT_CANCEL_UNSELECTED {}",status());return;
            }
            if(now-progressAt>2_000_000_000L){progressAt=now;GaslightProvider.LOG.info("GASLIGHT_CLIENT_PROGRESS {}",status());}
        });
        WorldRenderEvents.START.register(c->{if(!PortalRendering.isRendering()){frame++;hidden=true;views=0;camera=null;main=true;}});
        WorldRenderEvents.AFTER_SETUP.register(c->{if(offer==null||!c.world().dimension().equals(AutomaticPortalService.WORLD))return;
            try{Vec3 p=c.camera().getPosition();float[] matrix=new float[16];new Matrix4f(c.projectionMatrix()).mul(c.positionMatrix()).get(matrix);var clip=new FrameClip(p.x,p.y,p.z,matrix);views++;hidden&=offer.hidden(clip);if(!PortalRendering.isRendering())camera=clip;}
            catch(RuntimeException bad){hidden=false;camera=null;}
        });
        WorldRenderEvents.END.register(c->{if(PortalRendering.isRendering()||offer==null||!main)return;main=false;
            try{warm(Minecraft.getInstance());
                if(serverArmed&&fence.state()==GenerationFence.State.READY&&camera!=null&&views>0&&hidden&&c.world().dimension().equals(AutomaticPortalService.WORLD)&&c.camera().getEntity()==Minecraft.getInstance().player&&playerClearOfPrevious(Minecraft.getInstance())&&fence.select(offer.session(),offer.generation(),frame,true,true,System.nanoTime())){
                    heldPosition=Minecraft.getInstance().player.position();selectedAt=System.nanoTime();SceneVisibility.acknowledged=false;SceneVisibility.selected=new SceneVisibility.Identity(offer.session(),offer.generation());
                    ClientPlayNetworking.send(new SceneProtocol.Selection(offer.session(),offer.generation(),frame,camera));
                    GaslightProvider.LOG.info("GASLIGHT_CLIENT_SELECTED g={} frame={} views={}",offer.generation(),frame,views);
                }
            }catch(RuntimeException bad){fail("native scene preparation: "+bad);}
        });
    }
    private static void warm(Minecraft mc){
        if(fence==null||mc.level==null||fence.state()!=GenerationFence.State.PREPARING&&fence.state()!=GenerationFence.State.READY)return;
        int cx=Math.floorDiv((int)Math.floor(offer.destination().x),16),cz=Math.floorDiv((int)Math.floor(offer.destination().z),16);
        for(int x=cx-3;x<=cx+3;x++)for(int z=cz-3;z<=cz+3;z++)if(mc.level.getChunkSource().getChunk(x,z,ChunkStatus.FULL,false)==null){readiness="missing chunk "+x+","+z;fence.invalidateReady(offer.session(),offer.generation(),System.nanoTime());return;}
        boolean first=false,second=false;
        for(var entity:mc.level.entitiesForRendering()){first|=entity.getUUID().equals(offer.first());second|=entity.getUUID().equals(offer.second());}
        if(!first||!second||mc.level.getLightEngine().hasLightWork()){readiness="portals="+first+","+second+" lightWork="+mc.level.getLightEngine().hasLightWork();fence.invalidateReady(offer.session(),offer.generation(),System.nanoTime());return;}
        dataReady=true;
        if(!hashReady){
            BlockPos dest=BlockPos.containing(offer.destination());int height=offer.maxY()-offer.minY(),count=64*2*height,end=Math.min(hashCursor+4096,count);
            for(;hashCursor<end;hashCursor++){
                int tangent=hashCursor/(2*height)-32,normal=(hashCursor/height)%2-1,y=offer.minY()+hashCursor%height;
                boundary.add(mc.level.getBlockState(new BlockPos(dest.getX()+(offer.xPlane()?normal:tangent),y,dest.getZ()+(offer.xPlane()?tangent:normal))));
            }
            if(hashCursor<count)return;
            String actual=AutomaticPortalService.digest(boundary);
            if(!actual.equals(offer.seamDigest())){
                if(++digestRetries==1||digestRetries%32==0){
                    var source=new ArrayList<BlockState>();BlockPos origin=BlockPos.containing(offer.origin());String firstDifference="none";
                    for(int i=0;i<count;i++){
                        int tangent=i/(2*height)-32,normal=(i/height)%2-1,y=offer.minY()+i%height;
                        BlockPos pos=new BlockPos(origin.getX()+(offer.xPlane()?normal:tangent),y,origin.getZ()+(offer.xPlane()?tangent:normal));var state=mc.level.getBlockState(pos);source.add(state);
                        if(firstDifference.equals("none")&&state!=boundary.get(i))firstDifference="index="+i+" source="+pos+" "+state+" destination="+boundary.get(i);
                    }
                    GaslightProvider.LOG.info("GASLIGHT_SEAM_WAIT g={} retry={} expected={} actual={} nativeSource={} difference={}",offer.generation(),digestRetries,offer.seamDigest(),actual,AutomaticPortalService.digest(source),firstDifference);
                }
                boundary.clear();hashCursor=0;return;
            }
            hashReady=true;boundary.clear();
        }
        var area=((IEWorldRenderer)mc.levelRenderer).ip_getBuiltChunkStorage();if(!(area instanceof ImmPtlViewArea storage))throw new IllegalStateException("provider view-area contract unavailable");
        var dispatcher=mc.levelRenderer.getSectionRenderDispatcher();dispatcher.uploadAllPendingUploads();
        for(var it=waiting.entrySet().iterator();it.hasNext();){var e=it.next();var section=e.getKey();if(section.getCompiled()!=e.getValue()&&section.getCompiled()!=SectionRenderDispatcher.CompiledSection.UNCOMPILED&&!section.isDirty()){complete.add(section);it.remove();}}
        int issued=0,needed=0;var cache=new RenderRegionCache();var required=Collections.newSetFromMap(new IdentityHashMap<SectionRenderDispatcher.RenderSection,Boolean>());
        for(int x=cx-2;x<=cx+2;x++)for(int z=cz-2;z<=cz+2;z++){
            var chunk=mc.level.getChunkSource().getChunk(x,z,ChunkStatus.FULL,false);if(chunk==null)return;
            for(int index=0;index<chunk.getSections().length;index++){
                if(chunk.getSections()[index].hasOnlyAir())continue;
                int sy=chunk.getSectionYFromSectionIndex(index);var section=storage.provideBuiltChunkByChunkPos(x,sy,z);needed++;
                if(!section.getOrigin().equals(new BlockPos(x*16,sy*16,z*16)))throw new IllegalStateException("native section coordinate mismatch");
                required.add(section);
                if(section.isDirty())complete.remove(section);
                if(!complete.contains(section)&&!waiting.containsKey(section)&&issued<2&&waiting.size()<16){
                    if(section.getCompiled()==SectionRenderDispatcher.CompiledSection.EMPTY){
                        // EMPTY is a shared object; an identity-change fence can
                        // never observe a second successful empty compilation.
                        // Run the real native compiler synchronously for this
                        // case, within the same two-section per-frame budget.
                        section.compileSync(cache);section.setNotDirty();
                        if(section.getCompiled()!=SectionRenderDispatcher.CompiledSection.UNCOMPILED)complete.add(section);
                    }else{waiting.put(section,section.getCompiled());section.rebuildSectionAsync(dispatcher,cache);section.setNotDirty();}
                    issued++;
                }
            }
        }
        // View-area cache objects can be replaced during same-dimension travel.
        // Obsolete objects are neither readiness evidence nor retained ownership.
        complete.retainAll(required);waiting.keySet().retainAll(required);
        readiness="native meshes "+complete.size()+"/"+needed;
        if(needed!=complete.size()||!waiting.isEmpty()||mc.level.getLightEngine().hasLightWork())fence.invalidateReady(offer.session(),offer.generation(),System.nanoTime());
        if(needed==complete.size()&&waiting.isEmpty()&&!mc.level.getLightEngine().hasLightWork()&&fence.ready(offer.session(),offer.generation(),dataReady,hashReady,true,System.nanoTime())){
            ClientPlayNetworking.send(new SceneProtocol.Ready(offer.session(),offer.generation(),needed,64*2*(offer.maxY()-offer.minY()),offer.seamDigest()));
            long drawSections=complete.stream().filter(section->net.minecraft.client.renderer.RenderType.chunkBufferLayers().stream().anyMatch(layer->!section.getCompiled().isEmpty(layer))).count();
            GaslightProvider.LOG.info("GASLIGHT_CLIENT_READY g={} compiledSections={} geometrySections={} elapsedMs={}",offer.generation(),needed,drawSections,(System.nanoTime()-received)/1e6);
        }
    }
}
