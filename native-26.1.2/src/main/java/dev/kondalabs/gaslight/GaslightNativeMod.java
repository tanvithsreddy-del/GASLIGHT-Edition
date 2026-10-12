package dev.kondalabs.gaslight;

import java.nio.file.*;
import java.io.IOException;
import java.util.*;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.storage.LevelResource;

/** Standalone native traversal. NO dependency on a portal mod. 
 * Disabled by default; does not modify existing blocks. New remote chunk generation,
 * when explicitly enabled, may persist generated chunks to the world save.
 */
public final class GaslightNativeMod implements ModInitializer {
    private static boolean configuredEnabled;
    private static boolean allowRemoteChunkGeneration;
    public static boolean remoteGenerationPermitted(){return allowRemoteChunkGeneration;}
    private static boolean previewExperimental;
    private static boolean phase2StaticMode;
    private static boolean phase2AllowCrossing;
    private static boolean phase3DynamicMode;
    private static boolean phase3AllowPhysical;
    private static boolean phase3AllowCrossing;
    private static boolean phase3ClosedLoopMode;
    private static ProtectedChunkRegions.Policy extraProtected=ProtectedChunkRegions.parse(List.of());
    private static final Map<MinecraftServer,FixedPortalRig> RIGS=new WeakHashMap<>();
    private static final Map<MinecraftServer,Phase3Engine> PHASE3=new WeakHashMap<>();
    public static final String MOD_ID="gaslight_native";
    @Override public void onInitialize(){
        PortalSnapshotPayload.register();
        RemoteWorldSync.register();
        readConfig();
        extraProtected=ProtectedChunkRegions.load(Path.of("config","gaslight-protected-chunks.csv"));
        if(!extraProtected.valid())System.err.println("[GASLIGHT] fail closed: "+extraProtected.reason());
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if(phase3DynamicMode){
                Phase3Engine engine=PHASE3.computeIfAbsent(server,s->new Phase3Engine(
                    new VanillaHost(s),
                    new Phase3Engine.Settings(configuredEnabled,phase3AllowPhysical,
                        phase3AllowPhysical && phase3AllowCrossing,2,16,128,8,phase3ClosedLoopMode),0x6A54_2026_0003L));
                engine.tick();
                if(engine.status().ticks()%1200==0 && configuredEnabled)
                    System.out.println("[GASLIGHT Phase3] "+engine.status());
                return;
            }
            if(phase2StaticMode){
                FixedPortalRig rig=RIGS.computeIfAbsent(server,s->new FixedPortalRig(
                    new VanillaHost(s),configuredEnabled,phase2AllowCrossing,1024,1024));
                rig.tick();
                return;
            }
            // The earlier automatic physical scheduler is retired. It bypassed
            // Phase3's atomic and client-camera vetoes and must not start merely
            // because somebody toggled the general enable flag.
            return;
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            var p3=PHASE3.remove(server);
            if(p3!=null)p3.close();
            var rig=RIGS.remove(server);
            if(rig!=null)rig.close();
        });
        System.out.println("[GASLIGHT native] registered standalone traversal; auto="+configuredEnabled+
                            ", remote-generation="+allowRemoteChunkGeneration);
    }
    private static void readConfig(){
        try {
            Path file=Path.of("config","gaslight-native.properties");
            if(Files.notExists(file)){
                Files.createDirectories(file.getParent());
                Files.writeString(file,"# GASLIGHT native: experimental, back up a test world first.\n"+
                    "# ENABLE ONLY IN A DISPOSABLE SINGLEPLAYER WORLD!\n"+
                    "enable=false\n"+
                    "allowRemoteChunkGeneration=false\n"+
                    "# Debug HUD remote voxel sampling; never a seamless portal.\n"+
                    "previewExperimental=false\n"+
                    "# Phase 2: one permanent static test portal instead of dynamic scheduling.\n"+
                    "phase2StaticMode=false\n"+
                    "# Optional: enable native player crossing (unsafe until Minecraft-tested).\n"+
                    "phase2AllowCrossing=false\n"+
                    "# Phase3 logical 20-tick curves and protected paired-route planner.\n"+
                    "phase3DynamicMode=false\n"+
                    "# Disabled, even when phase3DynamicMode=true, until client-authoritative visibility exists.\n"+
                    "phase3AllowPhysical=false\n"+
                    "phase3AllowCrossing=false\n"+
                    "# Expensive bounded all-or-nothing closed-loop planning.\n"+
                    "phase3ClosedLoopMode=false\n");
                return;
            }
            Properties props=new Properties();
            try(var in=Files.newInputStream(file)){props.load(in);}
            configuredEnabled=Boolean.parseBoolean(props.getProperty("enable","false"));
            allowRemoteChunkGeneration=Boolean.parseBoolean(props.getProperty("allowRemoteChunkGeneration","false"));
            previewExperimental=Boolean.parseBoolean(props.getProperty("previewExperimental","false"));
            phase2StaticMode=Boolean.parseBoolean(props.getProperty("phase2StaticMode","false"));
            phase2AllowCrossing=Boolean.parseBoolean(props.getProperty("phase2AllowCrossing","false"));
            phase3DynamicMode=Boolean.parseBoolean(props.getProperty("phase3DynamicMode","false"));
            phase3AllowPhysical=Boolean.parseBoolean(props.getProperty("phase3AllowPhysical","false"));
            phase3AllowCrossing=Boolean.parseBoolean(props.getProperty("phase3AllowCrossing","false"));
            phase3ClosedLoopMode=Boolean.parseBoolean(props.getProperty("phase3ClosedLoopMode","false"));
        }catch(IOException e){configuredEnabled=false;allowRemoteChunkGeneration=false;previewExperimental=false;
            phase2StaticMode=false;phase2AllowCrossing=false;
            phase3DynamicMode=false;phase3AllowPhysical=false;phase3AllowCrossing=false;
            phase3ClosedLoopMode=false;
            System.err.println("[GASLIGHT native] fail closed: config unreadable: "+e);}
    }
    private static final class VanillaHost implements Phase3Engine.Host {
        private final MinecraftServer server;
        private int generatedChunks=0;
        private static final int GENERATION_BUDGET=64;
        private final Phase3RecoveryJournal.FileStore journal;
        private final Phase3PreparedCatalog.Result prepared;
        private VanillaHost(MinecraftServer server){
            this.server=server;
            // Journal is inside THIS WORLD's save, never shared between saves.
            // No physical creation can proceed if the old journal was dirty.
            Path dataDir=server.getWorldPath(LevelResource.ROOT).resolve("data");
            this.journal=new Phase3RecoveryJournal.FileStore(
                dataDir.resolve("gaslight-phase3-intents.bin"));
            this.prepared=Phase3PreparedCatalog.load(
                dataDir.resolve("gaslight-phase3-candidates.csv"),8);
            if(!prepared.valid())System.err.println("[GASLIGHT Phase3] catalog rejected: "+prepared.reason());
        }
        @Override public Optional<Phase3RecoveryJournal.Store> recoveryJournal(){return Optional.of(journal);}
        @Override public List<Geometry.Chunk> preparedDestinationOffsets(NativeWorldEngine.Actor actor){
            return prepared.offsets();
        }
        private ServerLevel level(){return server.overworld();}
        // Server yaw/pitch cannot establish the actual client viewpoint (third-person,
        // zoom, camera mods, interpolation). Therefore NEVER authorize physical Phase3
        // publication from this adapter. Logical planning can still run safely.
        @Override public boolean authoritativeCamera(NativeWorldEngine.Actor actor){return false;}
        @Override public Optional<Phase3Engine.VerticalSpan> verticalSpan(){
            return Optional.of(new Phase3Engine.VerticalSpan(level().getMinY(),level().getMaxY()));
        }
        @Override public Optional<Geometry.Camera> camera(NativeWorldEngine.Actor actor){
            try {return Optional.of(Geometry.Camera.fromYawPitch(
                actor.position().x(),actor.position().y()+1.62,actor.position().z(),
                actor.yaw(),actor.pitch(),110,16.0/9.0,128));}
            catch(RuntimeException exception){return Optional.empty();}
        }
        private int sampledSurface(int x,int z,int nearY){
            if(!level().getChunkSource().hasChunk(Math.floorDiv(x,16),Math.floorDiv(z,16)))return Integer.MIN_VALUE;
            int hi=Math.min(level().getMaxY()-2,nearY+12),lo=Math.max(level().getMinY(),nearY-12);
            for(int y=hi;y>=lo;y--){
                var state=level().getBlockState(new BlockPos(x,y,z));
                if(!state.isAir() && state.getFluidState().isEmpty())return y;
            }
            return Integer.MIN_VALUE;
        }
        // Real-world destination gate: nine interior columns from an ALREADY LOADED
        // chunk. No worldgen, no block modification, no synthetic biome fallback.
        @Override public boolean requireDetailedTerrainProfiles(){return true;}
        @Override public Optional<Phase3DestinationProfile.Profile> terrainProfile(Geometry.Chunk chunk){
            if(chunk==null||server.getPlayerList().getPlayers().size()!=1)return Optional.empty();
            if(!level().getChunkSource().hasChunk(chunk.x(),chunk.z()))return Optional.empty();
            int anchor=(int)Math.floor(server.getPlayerList().getPlayers().get(0).getY());
            final int lo=Math.max(level().getMinY()+1,anchor-16);
            final int hi=Math.min(level().getMaxY()-4,anchor+16);
            if(lo>=hi)return Optional.empty();
            final int[] grid={2,8,13};
            return Phase3DestinationProfile.capture(index->{
                try {
                    if(!level().getChunkSource().hasChunk(chunk.x(),chunk.z()))return Optional.empty();
                    int x=Math.addExact(Math.multiplyExact(chunk.x(),16),grid[index%3]);
                    int z=Math.addExact(Math.multiplyExact(chunk.z(),16),grid[index/3]);
                    int ground=Integer.MIN_VALUE,fluidTop=Integer.MIN_VALUE;
                    String groundId="";
                    Phase3DestinationProfile.Fluid fluid=Phase3DestinationProfile.Fluid.NONE;
                    Phase3DestinationProfile.Material material=Phase3DestinationProfile.Material.UNKNOWN;
                    for(int y=hi;y>=lo;y--){
                        var state=level().getBlockState(new BlockPos(x,y,z));
                        String id=state.getBlock().toString().toLowerCase(Locale.ROOT);
                        if(!state.getFluidState().isEmpty()){
                            var f=id.contains("lava") ? Phase3DestinationProfile.Fluid.LAVA :
                                id.contains("water") ? Phase3DestinationProfile.Fluid.WATER :
                                Phase3DestinationProfile.Fluid.UNKNOWN;
                            if(f==Phase3DestinationProfile.Fluid.UNKNOWN)return Optional.empty();
                            if(fluid!=Phase3DestinationProfile.Fluid.NONE && fluid!=f)return Optional.empty();
                            fluid=f;
                            fluidTop=Math.max(fluidTop,y);
                        }
                        if(!state.isAir()&&state.getFluidState().isEmpty()){
                            ground=y;
                            groundId=id;
                            material=Phase3DestinationProfile.materialFromId(id);
                            break;
                        }
                    }
                    if(ground==Integer.MIN_VALUE || material==Phase3DestinationProfile.Material.UNKNOWN)
                        return Optional.empty();
                    boolean clear=true;
                    for(int y=ground+1;y<=ground+2;y++){
                        var state=level().getBlockState(new BlockPos(x,y,z));
                        if(!state.isAir() && state.getFluidState().isEmpty()){clear=false;break;}
                    }
                    String biome=level().getBiome(new BlockPos(x,ground,z)).unwrapKey()
                        .map(key->key.identifier().toString()).orElse("");
                    if(biome.isBlank())return Optional.empty();
                    return Optional.of(new Phase3DestinationProfile.Column(ground,material,fluid,clear,biome,
                        groundId,fluidTop));
                }catch(RuntimeException ex){return Optional.empty();}
            });
        }
        @Override public double terrainScore(Geometry.Chunk source,Geometry.Chunk dest){
            if(server.getPlayerList().getPlayers().isEmpty())return Double.NaN;
            int playerY=(int)Math.floor(server.getPlayerList().getPlayers().get(0).getY());
            double total=0;
            int[] offsets={4,12};
            try{
                for(int sx:offsets)for(int sz:offsets){
                    int a=sampledSurface(source.x()*16+sx,source.z()*16+sz,playerY);
                    int b=sampledSurface(dest.x()*16+sx,dest.z()*16+sz,playerY);
                    if(a==Integer.MIN_VALUE||b==Integer.MIN_VALUE)return Double.NaN;
                    total+=Math.abs(a-b);
                }
            }catch(RuntimeException ex){return Double.NaN;}
            // Preliminary read-only height similarity, not a biome/lighting proof.
            return Math.max(0,1.0-total/(4*12.0));
        }
        @Override public boolean singleplayer(){return server.getPlayerList().getPlayers().size()==1;}
        @Override public Collection<NativeWorldEngine.Actor> players(){
            var result=new ArrayList<NativeWorldEngine.Actor>();
            for(ServerPlayer p:server.getPlayerList().getPlayers()){
                if(p.level()!=level())continue;
                result.add(new NativeWorldEngine.Actor(p.getUUID(),
                  new NativePortal.V(p.getX(),p.getY(),p.getZ()),
                  p.getYRot(),p.getXRot(),p.isSpectator(),p.isPassenger()));
            }
            return result;
        }
        @Override public boolean loadChunk(int cx,int cz){
            if(Math.abs((long)cx)>1_844_000 || Math.abs((long)cz)>1_844_000)return false;
            try{
                if(level().getChunkSource().hasChunk(cx,cz))return true;
                if(!allowRemoteChunkGeneration||generatedChunks>=GENERATION_BUDGET)return false;
                // Conservative rate limit is implemented in NativeWorldEngine (at most one/tick).
                boolean loaded=level().getChunk(cx,cz)!=null;
                if(loaded)generatedChunks++;
                return loaded;
            }catch(Throwable ex){System.err.println("[GASLIGHT native] chunk loading failed: "+ex);return false;}
        }
        @Override public StructureEnvelope.Result inspectChunk(int cx,int cz){
            if(!extraProtected.valid())return StructureEnvelope.Result.UNKNOWN;
            if(extraProtected.protectedAt(cx,cz))return StructureEnvelope.Result.USER_PROTECTED;
            try {
                if(!level().getChunkSource().hasChunk(cx,cz))return StructureEnvelope.Result.UNKNOWN;
                LevelChunk chunk=level().getChunk(cx,cz);
                if(chunk==null)return StructureEnvelope.Result.UNKNOWN;
                // Structures may START in a different chunk. References cover touching chunks.
                if(!chunk.getAllStarts().isEmpty()||!chunk.getAllReferences().isEmpty())
                    return StructureEnvelope.Result.STRUCTURE;
                return StructureEnvelope.Result.SAFE;
            }catch(Throwable ex){return StructureEnvelope.Result.UNKNOWN;}
        }
        @Override public void onPortalActivated(NativePortal.Passage passage) {
            if(previewExperimental)RemoteWorldSync.offer(server,passage);
        }
        @Override public void onPortalPreparing(NativePortal.Passage passage){RemoteWorldSync.hint(server,passage);}
        @Override public void onPortalPreparationCancelled(NativePortal.Passage passage){RemoteWorldSync.retire(server,passage);}
        @Override public boolean portalDestinationReady(NativePortal.Passage passage){return RemoteWorldSync.destinationReady(server,passage);}
        @Override public void onPortalRetired(NativePortal.Passage passage){
            RemoteWorldSync.retire(server,passage);
            ServerPlayer player=server.getPlayerList().getPlayer(passage.owner());
            if(player==null)return;
            try{
                if(ServerPlayNetworking.canSend(player,PortalSnapshotPayload.TYPE))
                    ServerPlayNetworking.send(player,new PortalSnapshotPayload(
                        PortalSnapshotWire.encode(PortalSnapshotWire.Message.retire(
                            passage.owner(),passage.generation()))));
            }catch(IOException | RuntimeException e){
                // Best-effort preview notification must never abort server shutdown.
                System.err.println("[GASLIGHT native] retire notification skipped: "+e);
            }
        }
        @Override public boolean canExit(NativeWorldEngine.Actor actor,NativePortal.V v){
            if(!Double.isFinite(v.x())||!Double.isFinite(v.y())||!Double.isFinite(v.z())||
                Math.abs(v.x())>29_500_000||Math.abs(v.z())>29_500_000)return false;
            int cx=(int)Math.floor(v.x()/16),cz=(int)Math.floor(v.z()/16);
            if(!level().getChunkSource().hasChunk(cx,cz))return false;
            int px=(int)Math.floor(v.x()),py=(int)Math.floor(v.y()),pz=(int)Math.floor(v.z());
            if(py < level().getMinY()+2 || py > level().getMaxY()-3)return false;
            // Reject edge cases where nearby blocks would force-load unknown adjacent chunks.
            for(int x=px-1;x<=px+1;x+=2)for(int z=pz-1;z<=pz+1;z+=2)
                if(!level().getChunkSource().hasChunk(Math.floorDiv(x,16),Math.floorDiv(z,16)))return false;
            // This first native build rejects unsafe destinations rather than suffocating players.
            // Plane traversal through walls/liquids needs a physics integration beyond vanilla teleport.
            for(int x=px-1;x<=px+1;x++)for(int z=pz-1;z<=pz+1;z++){
                for(int y=py;y<=py+1;y++)if(!level().getBlockState(new BlockPos(x,y,z)).isAir())return false;
            }
            return true;
        }
        @Override public boolean teleport(NativeWorldEngine.Actor actor,NativePortal.V target,double yaw){
            ServerPlayer p=server.getPlayerList().getPlayer(actor.id());
            if(p==null||p.level()!=level()||p.isPassenger()||p.isSpectator())return false;
            try{
                return p.teleportTo(level(),target.x(),target.y(),target.z(),
                    Set.of(),(float)yaw,p.getXRot(),false);
            }catch(Throwable ex){System.err.println("[GASLIGHT native] teleport failed: "+ex);return false;}
        }
    }
}
