package dev.kondalabs.gaslight.provider;

import dev.kondalabs.gaslight.NativePortal;
import dev.kondalabs.gaslight.NativePortal.*;
import dev.kondalabs.gaslight.Phase3RecoveryJournal;
import dev.kondalabs.gaslight.StructureEnvelope;
import dev.kondalabs.gaslight.ProtectedChunkRegions;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import qouteall.imm_ptl.core.api.PortalAPI;
import qouteall.imm_ptl.core.chunk_loading.ChunkLoader;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.IPGlobal;

/** Phase A fixed link only. Creation and retirement require no connected observers.
 * Dynamic publication/camera authority is intentionally not claimed or enabled. */
public final class FixedPortalAdapter {
    public static final String TAG="gaslight-phase-a:";
    public static final String MARKER="GASLIGHT_PHASE_A_DISPOSABLE_V1";
    public record Spec(ServerLevel source,Vec3 origin,ServerLevel target,Vec3 destination,double width,double height){}
    public record Token(UUID owner,long generation){}
    public record Status(String state,String reason,int portals,int ownedLoaders,int ownedChunks,int intents,long generation){}
    private final MinecraftServer server;
    private final Phase3RecoveryJournal.FileStore journal;
    private final Set<UUID> authorized=new HashSet<>();
    private final List<Portal> portals=new ArrayList<>();
    private final List<ChunkLoader> loaders=new ArrayList<>();
    private String state="DISABLED",reason="disposable marker absent";
    private Token token;private long generation;private boolean hadViewer;
    private Spec spec;
    private final int previousRadius,previousLayers,previousRenderLimit;

    FixedPortalAdapter(MinecraftServer server){
        this.server=server;
        previousRadius=IPGlobal.indirectLoadingRadiusCap;previousLayers=IPGlobal.maxPortalLayer;previousRenderLimit=IPGlobal.portalRenderLimit;
        Path root=server.getWorldPath(LevelResource.ROOT);
        boolean permitted=false;
        try { Path marker=root.resolve(".gaslight-phase-a-disposable");permitted=Files.isRegularFile(marker,LinkOption.NOFOLLOW_LINKS)&&Files.size(marker)<=128&&Files.readString(marker).trim().equals(MARKER); }
        catch(IOException|RuntimeException ignored) { /* Absent or unreadable authorization keeps all effects disabled. */ }
        journal=permitted?new Phase3RecoveryJournal.FileStore(root.resolve("data/gaslight-phase-a/intents.bin")):null;
        if(journal!=null){ state=journal.blockedOnOpen()?"QUARANTINED":"IDLE";reason=journal.diagnostic();IPGlobal.indirectLoadingRadiusCap=2;IPGlobal.maxPortalLayer=2;IPGlobal.portalRenderLimit=8; }
    }
    public static boolean owned(Portal p){return p.portalTag!=null&&p.portalTag.startsWith(TAG);}
    boolean permits(Portal p){return authorized.contains(p.getUUID()) && (state.equals("STAGING")||state.equals("ACTIVE"));}
    private void thread(){if(!server.isSameThread())throw new IllegalStateException("server thread required");}
    private boolean refuse(String why){reason=why;GaslightProvider.LOG.info("GASLIGHT_FIXED_REFUSED {}",why);return false;}
    public Status status(){thread();return new Status(state,reason,portals.size(),loaders.size(),loaders.stream().mapToInt(ChunkLoader::getChunkNum).sum(),journal==null?0:journal.outstanding(),generation);}
    public Token token(){thread();return token;}
    public boolean prepare(Spec s){
        thread();
        if(journal==null||journal.blockedOnOpen()||!state.equals("IDLE"))return refuse("disabled, outstanding recovery or active generation");
        if(!server.getPlayerList().getPlayers().isEmpty())return refuse("publication requires an observer-free server");
        try {
            Objects.requireNonNull(s);Objects.requireNonNull(s.source);Objects.requireNonNull(s.target);
            if(s.source.getServer()!=server||s.target.getServer()!=server)throw new IllegalArgumentException("foreign world");
            Surface a=surface(s.origin,s.width,s.height),b=surface(s.destination,s.width,s.height);
            if(s.width>16||s.height>16||s.width<2||s.height<3)throw new IllegalArgumentException("fixed prototype size budget");
            audit(s.source,a);audit(s.target,b);
            for(var side:List.of(Map.entry(s.source,s.origin),Map.entry(s.target,s.destination)))if(side.getKey().getEntities(null,box(side.getValue(),s.width,s.height).inflate(1)).stream().anyMatch(e->!e.isRemoved()))throw new IllegalArgumentException("occupied portal footprint");
            token=new Token(UUID.randomUUID(),++generation);spec=s;
            // NativePortal geometry is an intent carrier; provider API owns the actual rigid transform.
            if(!journal.begin(List.of(new Passage(a,b,token.owner,token.generation)))){state="QUARANTINED";return refuse("durable intent refused: "+journal.diagnostic());}
            state="STAGING";
            addLoader(s.source,s.origin);addLoader(s.target,s.destination);
            Portal forward=Portal.ENTITY_TYPE.create(s.source);
            if(forward==null)throw new IllegalStateException("provider entity creation failed");
            forward.setOriginPos(s.origin);forward.setOrientationAndSize(new Vec3(1,0,0),new Vec3(0,1,0),s.width,s.height);
            forward.setDestinationDimension(s.target.dimension());forward.setDestination(s.destination);
            forward.setScaleTransformation(1);forward.setRotation(null);
            var reverse=PortalAPI.createReversePortal(forward);
            for(Portal p:List.of(forward,PortalAPI.createFlippedPortal(forward),reverse,PortalAPI.createFlippedPortal(reverse))){
                p.portalTag=TAG+token.owner+":"+token.generation;
                authorized.add(p.getUUID());portals.add(p);
                if(!((ServerLevel)p.level()).addFreshEntity(p))throw new IllegalStateException("provider spawn refused");
            }
            state="ACTIVE";reason="fixed rigid link ready; automatic replacement disabled";hadViewer=false;
            GaslightProvider.LOG.info("GASLIGHT_FIXED_READY {}",status());return true;
        }catch(RuntimeException failure){
            reason=failure.toString();
            if(state.equals("STAGING")){rollback();state="QUARANTINED";} // Keep uncertain intent for recovery.
            return refuse(reason);
        }
    }
    private static Surface surface(Vec3 v,double w,double h){Objects.requireNonNull(v);return new Surface(new V(v.x,v.y,v.z),new V(1,0,0),w,h);}
    private static AABB box(Vec3 p,double w,double h){return new AABB(p.x-w/2,p.y-h/2,p.z-.1,p.x+w/2,p.y+h/2,p.z+.1);}
    private static void audit(ServerLevel world,Surface s){
        var policy=ProtectedChunkRegions.load(world.getServer().getWorldPath(LevelResource.ROOT).resolve("gaslight-protected-chunks.csv"));
        if(!policy.valid())throw new IllegalArgumentException(policy.reason());
        if(s.centre().y()-s.height()/2<world.getMinBuildHeight()||s.centre().y()+s.height()/2>world.getMaxBuildHeight())throw new IllegalArgumentException("build height");
        var result=StructureEnvelope.audit(s,1,25,(x,z)->{
            var chunk=world.getChunkSource().getChunkNow(x,z);
            if(chunk==null)return StructureEnvelope.Result.UNKNOWN;
            if(policy.protectedAt(x,z))return StructureEnvelope.Result.USER_PROTECTED;
            if(!world.getWorldBorder().isWithinBounds(new BlockPos(x*16,(int)s.centre().y(),z*16)))return StructureEnvelope.Result.UNKNOWN;
            if(chunk.getAllStarts().values().stream().anyMatch(start->start!=null&&start.isValid())||chunk.getAllReferences().values().stream().anyMatch(ref->!ref.isEmpty()))return StructureEnvelope.Result.STRUCTURE;
            return StructureEnvelope.Result.SAFE;
        });
        if(!result.accepted())throw new IllegalArgumentException(result.reason());
        // Provider lease touches 5x5; unknown/structured lease dependencies are vetoes too.
        int cx=Math.floorDiv((int)Math.floor(s.centre().x()),16),cz=Math.floorDiv((int)Math.floor(s.centre().z()),16);
        for(int x=cx-2;x<=cx+2;x++)for(int z=cz-2;z<=cz+2;z++){
            var chunk=world.getChunkSource().getChunkNow(x,z);
            if(chunk==null||policy.protectedAt(x,z)||!world.getWorldBorder().isWithinBounds(new BlockPos(x*16,(int)s.centre().y(),z*16))||chunk.getAllStarts().values().stream().anyMatch(start->start!=null&&start.isValid())||chunk.getAllReferences().values().stream().anyMatch(ref->!ref.isEmpty()))throw new IllegalArgumentException("uncertified lease footprint");
        }
    }
    private void addLoader(ServerLevel world,Vec3 p){
        var loader=new ChunkLoader(world.dimension(),Math.floorDiv((int)Math.floor(p.x),16),Math.floorDiv((int)Math.floor(p.z),16),2);
        PortalAPI.addGlobalChunkLoader(server,loader);loaders.add(loader);
    }
    private void rollback(){
        for(Portal p:portals){p.setTeleportable(false);p.setIsVisible(false);p.discard();}portals.clear();authorized.clear();
        for(ChunkLoader l:loaders)PortalAPI.removeGlobalChunkLoader(server,l);loaders.clear();
    }
    public boolean retire(Token requested){
        thread();if(token!=requested||!state.equals("ACTIVE"))return refuse("stale retirement token");
        if(!server.getPlayerList().getPlayers().isEmpty())return refuse("retirement requires no observers");
        for(Portal p:portals)if(!p.level().getEntities(p,p.getBoundingBox().inflate(1),e->!(e instanceof Portal)).isEmpty())return refuse("crossing occupancy");
        rollback();
        // Vanilla save completion is not an authenticated durable entity-removal receipt.
        // Keep the intent until Phase C supplies crash reconciliation; never silently settle it.
        if(!server.saveEverything(false,true,true)){state="QUARANTINED";return refuse("retirement save failure; unresolved intent retained");}
        state="RETIRED_PENDING_RECOVERY";reason="closed; durable intent retained for crash reconciliation";spec=null;hadViewer=false;
        GaslightProvider.LOG.info("GASLIGHT_FIXED_RETIRED {}",status());return true;
    }
    void tick(){thread();if(state.equals("ACTIVE")){if(!server.getPlayerList().getPlayers().isEmpty())hadViewer=true;else if(hadViewer)retire(token);}}
    void shutdown(){thread();if(state.equals("ACTIVE")&&!retire(token)){rollback();state="QUARANTINED";reason="shutdown retains unresolved intent";}if(journal!=null){IPGlobal.indirectLoadingRadiusCap=previousRadius;IPGlobal.maxPortalLayer=previousLayers;IPGlobal.portalRenderLimit=previousRenderLimit;}}
}
