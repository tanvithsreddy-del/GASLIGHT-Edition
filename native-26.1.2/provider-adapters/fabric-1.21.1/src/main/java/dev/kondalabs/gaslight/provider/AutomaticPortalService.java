package dev.kondalabs.gaslight.provider;

import java.util.*;
import dev.kondalabs.gaslight.NativePortal.*;
import dev.kondalabs.gaslight.ProtectedChunkRegions;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import qouteall.imm_ptl.core.api.PortalAPI;
import qouteall.imm_ptl.core.chunk_loading.ChunkLoader;
import qouteall.imm_ptl.core.portal.*;

/** Private-world gameplay controller. Native source terrain is read, never erased.
 * A staged scene owns tickets and a durable intent before it can be offered. */
public final class AutomaticPortalService {
    static final ResourceKey<Level> WORLD=ResourceKey.create(Registries.DIMENSION,ResourceLocation.fromNamespaceAndPath("gaslight_provider","unobserved"));
    private final MinecraftServer server;
    private ServerPlayer player;
    private ServerLevel world,returnWorld;
    private Vec3 returnPosition;
    private UUID session;
    private SqliteIntentStore journal;
    private SceneTraffic traffic;
    private SceneLease entering;
    private Scene staged,current;
    private String state="IDLE",reason="use /gaslight begin to enter a private world";
    private long lastProposal,receivedWindow;
    private int packets;
    private final Set<UUID> permits=new HashSet<>();
    private final SceneWorkBudget workBudget=new SceneWorkBudget(System.nanoTime());
    private int oldRadius,oldLayers,oldRenderLimit;
    static String digest(List<BlockState> blocks){try{var hash=java.security.MessageDigest.getInstance("SHA-256");for(var s:blocks){int n=net.minecraft.world.level.block.Block.getId(s);hash.update((byte)(n>>>24));hash.update((byte)(n>>>16));hash.update((byte)(n>>>8));hash.update((byte)n);}return HexFormat.of().formatHex(hash.digest());}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    private static final class Scene {
        long generation,start=System.nanoTime(),finishedCopyAt,offeredAt;
        Vec3 origin,destination;boolean xPlane;int sign,minY,maxY;
        SceneLease lease;ChunkLoader loader;GenerationFence fence;SceneProtocol.Offer offer;
        final List<Portal> portals=new ArrayList<>();
        final List<BlockPos> seam=new ArrayList<>();final List<BlockState> states=new ArrayList<>();
        final List<BlockPos> sourceCells=new ArrayList<>();
        int copying;
        boolean clientReady,armed;
    }
    AutomaticPortalService(MinecraftServer server){this.server=server;}
    private void thread(){if(!server.isSameThread())throw new IllegalStateException("server thread required");}
    public String status(){return "state="+state+",reason="+reason+",generation="+(current==null?0:current.generation)+",staged="+(staged==null?0:staged.generation)+",origin="+(current==null?null:current.origin)+",destination="+(current==null?null:current.destination)+",portals="+permits.size()+",tickets="+((entering==null?0:entering.tickets())+(staged==null?0:staged.lease.tickets())+(current==null?0:current.lease.tickets()))+",intents="+(journal==null?0:journal.outstanding())+","+(traffic==null?"traffic=inactive":traffic.status());}
    public boolean begin(ServerPlayer requested){
        thread();if(player!=null||server.getPlayerList().getPlayers().size()!=1||requested.isPassenger()||!ServerPlayNetworking.canSend(requested,SceneProtocol.Offer.TYPE)){reason="one compatible unmounted player is required";return false;}
        if(!SceneProtocol.supported(requested)){reason="tested profile requires a GASLIGHT client, vanilla renderer and render distance 2–4";return false;}
        world=server.getLevel(WORLD);if(world==null){reason="private dimension unavailable";return false;}
        journal=new SqliteIntentStore(server.getWorldPath(LevelResource.ROOT).resolve("dimensions/gaslight_provider/unobserved/data/scene-intents.sqlite"),2);
        if(!journal.reconcileWithLoadVeto()){reason=journal.diagnostic();GaslightProvider.LOG.error("GASLIGHT_BEGIN_REFUSED {}",reason);journal.close();journal=null;return false;}
        SqliteIntentStore.Anchor anchor;
        if(requested.level().dimension().equals(WORLD))anchor=journal.loadAnchor(requested.getUUID());
        else{anchor=new SqliteIntentStore.Anchor(requested.level().dimension().location().toString(),requested.getX(),requested.getY(),requested.getZ());if(!journal.saveAnchor(requested.getUUID(),anchor))anchor=null;}
        if(anchor==null){reason="original return point unavailable: "+journal.diagnostic();journal.close();journal=null;return false;}
        returnWorld=server.getLevel(ResourceKey.create(Registries.DIMENSION,ResourceLocation.parse(anchor.dimension())));
        if(returnWorld==null){reason="original return dimension unavailable";journal.close();journal=null;return false;}
        player=requested;returnPosition=new Vec3(anchor.x(),anchor.y(),anchor.z());session=UUID.randomUUID();
        oldRadius=qouteall.imm_ptl.core.IPGlobal.indirectLoadingRadiusCap;oldLayers=qouteall.imm_ptl.core.IPGlobal.maxPortalLayer;oldRenderLimit=qouteall.imm_ptl.core.IPGlobal.portalRenderLimit;
        qouteall.imm_ptl.core.IPGlobal.indirectLoadingRadiusCap=3;qouteall.imm_ptl.core.IPGlobal.maxPortalLayer=2;qouteall.imm_ptl.core.IPGlobal.portalRenderLimit=4;
        try{traffic=SceneTraffic.attach(player);entering=new SceneLease(world,512,512,2);state="ENTERING";reason="preparing native private-world arrival";return true;}
        catch(RuntimeException bad){fail(bad.toString());return false;}
    }
    boolean permits(Portal p){return permits.contains(p.getUUID());}
    boolean active(String tag){var id=SceneVisibility.parse(tag);return id!=null&&session!=null&&id.session().equals(session)&&current!=null&&id.generation()==current.generation&&current.fence.state()==GenerationFence.State.COMMITTED;}
    private void audit(Vec3 origin,boolean xPlane){
        var policy=ProtectedChunkRegions.load(server.getWorldPath(LevelResource.ROOT).resolve("gaslight-protected-chunks.csv"));
        if(!policy.valid())throw new IllegalStateException(policy.reason());
        var surface=new Surface(new V(origin.x,origin.y,origin.z),xPlane?new V(0,0,1):new V(1,0,0),64,world.getHeight());
        String[] detail={""};
        var verdict=dev.kondalabs.gaslight.StructureEnvelope.audit(surface,1,64,(x,z)->{
            var chunk=world.getChunkSource().getChunkNow(x,z);
            if(chunk==null)return dev.kondalabs.gaslight.StructureEnvelope.Result.UNKNOWN;
            if(policy.protectedAt(x,z))return dev.kondalabs.gaslight.StructureEnvelope.Result.USER_PROTECTED;
            if(chunk.getAllStarts().values().stream().anyMatch(s->s!=null&&s.isValid())||chunk.getAllReferences().values().stream().anyMatch(r->!r.isEmpty())){
                var registry=world.registryAccess().registryOrThrow(Registries.STRUCTURE);
                detail[0]=" actual="+chunk.getPos()+" starts="+chunk.getAllStarts().entrySet().stream().filter(e->e.getValue().isValid()).limit(8).map(e->registry.getKey(e.getKey())+"@"+e.getValue().getChunkPos()).toList()+" references="+chunk.getAllReferences().entrySet().stream().filter(e->!e.getValue().isEmpty()).limit(8).map(e->registry.getKey(e.getKey())+":"+e.getValue().size()).toList();
                return dev.kondalabs.gaslight.StructureEnvelope.Result.STRUCTURE;
            }
            return dev.kondalabs.gaslight.StructureEnvelope.Result.SAFE;
        });
        if(!verdict.accepted())throw new IllegalStateException(verdict.reason()+detail[0]);
    }
    private void propose(){
        if(journal.blockedOnOpen())throw new IllegalStateException(journal.diagnostic());
        Scene s=new Scene();s.generation=journal.nextGeneration();s.minY=world.getMinBuildHeight();s.maxY=world.getMaxBuildHeight();
        Vec3 forward=player.getLookAngle();s.xPlane=Math.abs(forward.x)>Math.abs(forward.z);s.sign=(s.xPlane?forward.x:forward.z)>=0?1:-1;
        double coord=s.xPlane?player.getX():player.getZ();double plane=s.sign>0?Math.floor(coord)-4:Math.ceil(coord)+4;
        s.origin=new Vec3(s.xPlane?plane:Math.floor(player.getX())+.5,(s.minY+s.maxY)/2.0,s.xPlane?Math.floor(player.getZ())+.5:plane);
        try{audit(s.origin,s.xPlane);}catch(IllegalStateException veto){reason="source safety veto: "+veto.getMessage();state=current==null?"WAITING_SAFE_SITE":"ACTIVE";return;}
        Runtime runtime=Runtime.getRuntime();
        if(!workBudget.consume(System.nanoTime(),runtime.totalMemory()-runtime.freeMemory(),runtime.maxMemory())){reason="preparation rate or memory budget exhausted";state=current==null?"WAITING_BUDGET":"ACTIVE";return;}
        // Every reservation occupies a new native site; aborted attempts are retained too.
        int cell=(int)s.generation;if(cell>4000)throw new IllegalStateException("retained history budget; checkpoint required");
        s.destination=new Vec3(8192+(cell%64)*256+(s.origin.x-Math.floor(s.origin.x)),s.origin.y,16384+(cell/64)*256+(s.origin.z-Math.floor(s.origin.z)));
        Surface a=new Surface(new V(s.origin.x,s.origin.y,s.origin.z),s.xPlane?new V(0,0,1):new V(1,0,0),64,s.maxY-s.minY);
        Surface b=new Surface(new V(s.destination.x,s.destination.y,s.destination.z),a.tangent(),64,a.height());
        if(!journal.begin(List.of(new Passage(a,b,session,s.generation))))throw new IllegalStateException(journal.diagnostic());
        // A 64-block seam plus a 17-block structure halo can touch cx-4.
        // The three-chunk presentation window alone cannot certify that column.
        s.lease=new SceneLease(world,Math.floorDiv((int)Math.floor(s.destination.x),16),Math.floorDiv((int)Math.floor(s.destination.z),16),4,true);
        s.fence=new GenerationFence(session,s.generation,System.nanoTime()+60_000_000_000L);staged=s;state="PREPARING";reason="native terrain generation and full-height seam preparation";
        GaslightProvider.LOG.info("GASLIGHT_SCENE_PREPARING g={} dependencyFootprint={}",s.generation,s.lease.dependencies);
    }
    private void captureSeam(Scene s){
        audit(s.origin,s.xPlane);audit(s.destination,s.xPlane);
        BlockPos a=BlockPos.containing(s.origin),b=BlockPos.containing(s.destination);
        for(int tangent=-32;tangent<32;tangent++)for(int normal=-1;normal<=0;normal++)for(int y=s.minY;y<s.maxY;y++) {
            BlockPos source=new BlockPos(a.getX()+(s.xPlane?normal:tangent),y,a.getZ()+(s.xPlane?tangent:normal));
            var block=world.getBlockState(source);
            if(block.hasBlockEntity())throw new IllegalStateException("block entity intersects seam");
            var target=new BlockPos(b.getX()+(s.xPlane?normal:tangent),y,b.getZ()+(s.xPlane?tangent:normal));
            if(world.getBlockState(target).hasBlockEntity())throw new IllegalStateException("destination block entity intersects seam");
            s.seam.add(target);
            s.states.add(block);
            s.sourceCells.add(source);
        }
        var bounds=new net.minecraft.world.level.levelgen.structure.BoundingBox(b.getX()+(s.xPlane?-1:-32),s.minY,b.getZ()+(s.xPlane?-32:-1),b.getX()+(s.xPlane?0:31),s.maxY-1,b.getZ()+(s.xPlane?31:0));
        // These positions belong to a new, unpublished private reservation. Old
        // generation ticks must not be applied to their transplanted states.
        world.getBlockTicks().clearArea(bounds);world.getFluidTicks().clearArea(bounds);
    }
    private void offer(Scene s){
        audit(s.origin,s.xPlane);audit(s.destination,s.xPlane);
        if(!seamUnchanged(s))throw new IllegalStateException("source or destination seam changed during preparation");
        Portal p=ScenePortal.TYPE.create(world);if(p==null)throw new IllegalStateException("scene portal creation");
        p.setOriginPos(s.origin);p.setOrientationAndSize(s.xPlane?new Vec3(0,0,-s.sign):new Vec3(s.sign,0,0),new Vec3(0,1,0),64,s.maxY-s.minY);p.setDestinationDimension(WORLD);p.setDestination(s.destination);p.setRotation(null);p.setScaleTransformation(1);
        Portal flipped=PortalManipulation.createFlippedPortal(p,ScenePortal.TYPE),reverse=PortalManipulation.createReversePortal(p,ScenePortal.TYPE),reverseFlipped=PortalManipulation.createFlippedPortal(reverse,ScenePortal.TYPE);
        for(Portal portal:List.of(p,flipped,reverse,reverseFlipped)) {
            portal.portalTag=SceneVisibility.PREFIX+session+":"+s.generation;portal.specificPlayerId=player.getUUID();permits.add(portal.getUUID());s.portals.add(portal);
            if(!world.addFreshEntity(portal))throw new IllegalStateException("scene spawn refused");
        }
        s.offer=new SceneProtocol.Offer(session,s.generation,s.origin,s.destination,s.xPlane,s.sign,s.minY,s.maxY,p.getUUID(),flipped.getUUID(),current==null?null:current.origin,current==null?null:current.destination,current!=null&&current.xPlane,current==null?1:current.sign,digest(s.states));
        s.offeredAt=System.nanoTime();s.fence=new GenerationFence(session,s.generation,s.offeredAt+45_000_000_000L);
        ServerPlayNetworking.send(player,s.offer);state="OFFERED";reason="waiting for real client data, lighting, meshes and an excluded rendered frame";
        GaslightProvider.LOG.info("GASLIGHT_SCENE_OFFERED g={} cells={} {} -> {} digest={} samples={}",s.generation,s.seam.size(),s.origin,s.destination,digest(s.states),List.of(s.states.getFirst(),s.states.get((s.maxY-s.minY)/2),s.states.get(32*2*(s.maxY-s.minY)+(s.maxY-s.minY)/2)));
    }
    void clientReady(ServerPlayer sender,SceneProtocol.Ready packet){
        thread();
        if(sender!=player||staged==null||staged.offer==null||!packet.session().equals(session)||packet.generation()!=staged.generation)return;
        if(packet.cells()!=staged.states.size()||!packet.seamDigest().equals(staged.offer.seamDigest())||!seamUnchanged(staged)){GaslightProvider.LOG.info("GASLIGHT_READINESS_REFUSED g={} cells={} digestMatch={} difference={}",packet.generation(),packet.cells(),packet.seamDigest().equals(staged.offer.seamDigest()),seamDifference(staged));return;}
        staged.clientReady=true;
        GaslightProvider.LOG.info("GASLIGHT_CLIENT_READINESS_RECEIPT g={} compiledSections={} cells={}",packet.generation(),packet.meshes(),packet.cells());arm(staged);
    }
    private boolean clear(Scene scene){return scene==null||scene.portals.stream().allMatch(p->p.level().getEntities(p,p.getBoundingBox().inflate(.75),e->!(e instanceof Portal)).isEmpty());}
    private void arm(Scene scene){
        if(scene.armed||!scene.clientReady||!seamUnchanged(scene)||!clear(scene)||!clear(current))return;
        if(scene.fence.ready(session,scene.generation,true,true,true,System.nanoTime())){
            scene.armed=true;ServerPlayNetworking.send(player,new SceneProtocol.Armed(session,scene.generation));GaslightProvider.LOG.info("GASLIGHT_SERVER_ARMED g={}",scene.generation);
        }
    }
    void cancel(ServerPlayer sender,SceneProtocol.Cancel packet){
        thread();
        if(sender!=player||staged==null||!packet.session().equals(session)||packet.generation()!=staged.generation||staged.fence.state()==GenerationFence.State.SELECTING||staged.fence.state()==GenerationFence.State.COMMITTED)return;
        long generation=staged.generation;staged.fence.fail();release(staged,true);staged=null;
        reason="unselected offer cancelled; active scene preserved";state=current==null?"ACTIVE_EMPTY":"ACTIVE";lastProposal=System.nanoTime();
        ServerPlayNetworking.send(sender,new SceneProtocol.Withdraw(session,generation));GaslightProvider.LOG.info("GASLIGHT_SCENE_CANCELLED g={} {}",generation,status());
    }
    void select(ServerPlayer sender,SceneProtocol.Selection packet){
        thread();long now=System.nanoTime();if(now-receivedWindow>1_000_000_000L){receivedWindow=now;packets=0;}
        boolean accepted=false;
        if(++packets<=12&&sender==player&&staged!=null&&staged.offer!=null&&sender.level().dimension().equals(WORLD)&&!sender.isPassenger()) {
            Scene s=staged;double distance=sender.getEyePosition().distanceToSqr(new Vec3(packet.clip().x(),packet.clip().y(),packet.clip().z()));
            double side=(s.xPlane?sender.getX()-s.origin.x:sender.getZ()-s.origin.z)*s.sign;
            boolean unoccupied=s.portals.stream().allMatch(p->p.level().getEntities(p,p.getBoundingBox().inflate(.75),e->!(e instanceof Portal)).isEmpty());
            boolean oldClear=current==null||current.portals.stream().allMatch(p->p.level().getEntities(p,p.getBoundingBox().inflate(.75),e->!(e instanceof Portal)).isEmpty());
            GaslightProvider.LOG.info("GASLIGHT_SELECTION_GATE g={} eyeDistanceSq={} side={} sourceDistanceSq={} newClear={} oldClear={} seamStable={} hidden={} serverFence={}",s.generation,distance,side,sender.position().distanceToSqr(new Vec3(s.origin.x,sender.getY(),s.origin.z)),unoccupied,oldClear,seamUnchanged(s),s.offer.hidden(packet.clip()),s.fence.state());
            if(distance<36&&side>1&&sender.position().distanceToSqr(new Vec3(s.origin.x,sender.getY(),s.origin.z))<256&&unoccupied&&oldClear&&seamUnchanged(s)&&s.offer.hidden(packet.clip())&&s.fence.select(packet.session(),packet.generation(),packet.frame(),true,true,now)) {
                if(current!=null){if(!current.fence.retire(session,current.generation,oldClear,true))throw new IllegalStateException("active retirement fence refused");release(current,true);}
                accepted=s.fence.commit(packet.session(),packet.generation(),packet.frame(),now);
                if(accepted){current=s;staged=null;state="ACTIVE";reason="atomic scene selected";lastProposal=now;GaslightProvider.LOG.info("GASLIGHT_SCENE_COMMITTED g={} frame={} {}",s.generation,packet.frame(),status());}
            }
        }
        ServerPlayNetworking.send(sender,new SceneProtocol.Ack(packet.session(),packet.generation(),packet.frame(),accepted));
        if(!accepted&&sender==player)fail("scene selection rejected; retained recovery intent");
    }
    private boolean seamUnchanged(Scene s){for(int i=0;i<s.states.size();i++)if(world.getBlockState(s.sourceCells.get(i))!=s.states.get(i)||world.getBlockState(s.seam.get(i))!=s.states.get(i))return false;return true;}
    private String seamDifference(Scene s){for(int i=0;i<s.states.size();i++){var a=world.getBlockState(s.sourceCells.get(i));var b=world.getBlockState(s.seam.get(i));if(a!=s.states.get(i)||b!=s.states.get(i))return "index="+i+" expected="+s.states.get(i)+" source="+s.sourceCells.get(i)+":"+a+" destination="+s.seam.get(i)+":"+b;}return "stable";}
    private void release(Scene s,boolean settle){
        for(Portal p:s.portals){permits.remove(p.getUUID());p.setTeleportable(false);p.discard();}s.portals.clear();
        if(s.loader!=null&&player!=null)PortalAPI.removeChunkLoaderForPlayer(player,s.loader);
        if(s.lease!=null)s.lease.close();
        if(settle&&!journal.settle(session,s.generation))throw new IllegalStateException("durable retirement failed: "+journal.diagnostic());
    }
    void tick(){
        thread();if(player==null||state.equals("FAILED"))return;
        if(player.isRemoved()||server.getPlayerList().getPlayers().size()!=1){disconnected(player);return;}
        try {
            if(entering!=null&&entering.tick()) {
                int y=world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,8192,8192);
                if(!world.getFluidState(new BlockPos(8192,y-1,8192)).isEmpty())throw new IllegalStateException("unsafe private arrival");
                PortalAPI.teleportEntity(player,world,new Vec3(8192.5,y+1,8192.5));entering.close();entering=null;state="ACTIVE_EMPTY";lastProposal=System.nanoTime()+3_000_000_000L;
            }
            if(entering!=null)return;
            if(!player.level().dimension().equals(WORLD)){disconnected(player);return;}
            if(staged!=null&&staged.offer==null&&player.position().distanceToSqr(new Vec3(staged.origin.x,player.getY(),staged.origin.z))>=256){
                reason="source moved during preparation; unpublished reservation retired";GaslightProvider.LOG.info("GASLIGHT_CANDIDATE_VETO g={} {}",staged.generation,reason);release(staged,true);staged=null;state=current==null?"ACTIVE_EMPTY":"ACTIVE";return;
            }
            if(staged!=null&&player.position().distanceToSqr(new Vec3(staged.destination.x,player.getY(),staged.destination.z))<128*128)throw new IllegalStateException("player observed or approached the unpublished private reservation");
            if(staged==null&&System.nanoTime()-lastProposal>1_000_000_000L){lastProposal=System.nanoTime();
                int cx=Math.floorDiv((int)Math.floor(player.getX()),16),cz=Math.floorDiv((int)Math.floor(player.getZ()),16);boolean loaded=true;
                for(int x=cx-3;x<=cx+3;x++)for(int z=cz-3;z<=cz+3;z++)loaded&=world.getChunkSource().getChunkNow(x,z)!=null;
                if(loaded)propose();else reason="waiting for native source footprint";
            }
            if(staged!=null&&staged.offer==null&&staged.lease.tick()) {
                if(staged.loader==null){staged.loader=new ChunkLoader(WORLD,Math.floorDiv((int)Math.floor(staged.destination.x),16),Math.floorDiv((int)Math.floor(staged.destination.z),16),3);PortalAPI.addChunkLoaderForPlayer(player,staged.loader);return;}
                // FULL chunks alone do not run queued block physics. Prepare
                // genuine ticking chunks before copying, not after the offer.
                int dx=Math.floorDiv((int)Math.floor(staged.destination.x),16),dz=Math.floorDiv((int)Math.floor(staged.destination.z),16);
                for(int x=dx-3;x<=dx+3;x++)for(int z=dz-3;z<=dz+3;z++){
                    var holder=((qouteall.imm_ptl.core.ducks.IEChunkMap)world.getChunkSource().chunkMap).ip_getChunkHolder(net.minecraft.world.level.ChunkPos.asLong(x,z));
                    if(holder==null||holder.getTickingChunk()==null)return;
                }
                if(staged.states.isEmpty()){
                    try{captureSeam(staged);}catch(IllegalStateException veto){reason="candidate safety veto: "+veto.getMessage();GaslightProvider.LOG.info("GASLIGHT_CANDIDATE_VETO g={} {}",staged.generation,reason);release(staged,true);staged=null;state=current==null?"WAITING_SAFE_SITE":"ACTIVE";return;}
                }
                int end=Math.min(staged.copying+2048,staged.seam.size());
                if(player.position().distanceToSqr(new Vec3(staged.destination.x,player.getY(),staged.destination.z))<128*128)throw new IllegalStateException("player approached private reservation during copying");
                for(;staged.copying<end;staged.copying++)SceneTerrainCopy.block(world,staged.seam.get(staged.copying),staged.states.get(staged.copying));
                if(staged.copying==staged.seam.size()){
                    if(staged.finishedCopyAt==0)staged.finishedCopyAt=System.nanoTime();
                    if(System.nanoTime()-staged.finishedCopyAt>2_000_000_000L&&!world.getChunkSource().getLightEngine().hasLightWork()){
                        if(!seamUnchanged(staged)){reason="native block physics changed the copied seam; candidate retired";GaslightProvider.LOG.info("GASLIGHT_CANDIDATE_VETO g={} {}",staged.generation,reason);release(staged,true);staged=null;state=current==null?"WAITING_SAFE_SITE":"ACTIVE";return;}
                        offer(staged);
                    }
                }
            }
            if(staged!=null&&staged.offer!=null)arm(staged);
            if(staged!=null&&(staged.offeredAt==0?System.nanoTime()-staged.start>60_000_000_000L:System.nanoTime()-staged.offeredAt>44_000_000_000L))throw new IllegalStateException("scene preparation/selection timeout");
        }catch(RuntimeException failure){fail(failure.toString());}
    }
    private void fail(String why){reason=why;GaslightProvider.LOG.error("GASLIGHT_GAMEPLAY_FAILED {}",why);state="FAILED";if(entering!=null){entering.close();entering=null;}boolean offered=staged!=null&&staged.offer!=null;if(staged!=null){release(staged,false);staged=null;}if(offered&&player!=null)player.connection.disconnect(net.minecraft.network.chat.Component.literal("GASLIGHT retained a failed publication intent: "+why));}
    void disconnected(ServerPlayer who){if(who!=player)return;shutdown();}
    public void leave(){thread();if(player!=null&&returnWorld!=null&&returnPosition!=null)PortalAPI.teleportEntity(player,returnWorld,returnPosition);shutdown();}
    void shutdown(){thread();if(entering!=null){entering.close();entering=null;}if(staged!=null){release(staged,false);staged=null;}if(current!=null){release(current,false);current=null;}permits.clear();if(traffic!=null){traffic.close();traffic=null;}if(player!=null){qouteall.imm_ptl.core.IPGlobal.indirectLoadingRadiusCap=oldRadius;qouteall.imm_ptl.core.IPGlobal.maxPortalLayer=oldLayers;qouteall.imm_ptl.core.IPGlobal.portalRenderLimit=oldRenderLimit;}player=null;session=null;if(journal!=null){journal.close();journal=null;}state="IDLE";}
}
