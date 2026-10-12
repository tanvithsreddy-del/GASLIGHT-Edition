package dev.kondalabs.gaslight.client;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import dev.kondalabs.gaslight.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.nbt.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.biome.Biome;
import static dev.kondalabs.gaslight.RemoteWorldWire.*;

/** Render-data API in a distinct namespace. Never handles a vanilla chunk packet,
 * creates a ClientLevel, attaches a block entity to mc.level, or inserts light data
 * into the ordinary client engine. Unknown is Optional.empty, never air. */
public final class RemoteWorldClient {
    private static final RemoteWorldCache CACHE=new RemoteWorldCache(System::nanoTime);
    private static long packets,wireBytes,rejected,commits,decodeNanos,peakDecodeNanos;
    private static String sourceDimension;private static boolean preview;
    public record Namespace(UUID session,UUID portal,String dimension){}
    public static final class View {
        private final Namespace namespace;private final Scope scope;
        private View(Scope scope){this.scope=scope;namespace=new Namespace(scope.session(),scope.portal(),scope.destinationDimension());}
        public Namespace namespace(){return namespace;}public Scope scope(){return scope;}
        private Optional<RemoteChunkData> data(BlockPos pos){
            var mc=Minecraft.getInstance();if(mc.level==null||!mc.level.dimension().identifier().toString().equals(scope.sourceDimension())){CACHE.clear();return Optional.empty();}
            if(CACHE.scope().isEmpty()||!CACHE.scope().get().equals(scope))return Optional.empty();
            return CACHE.chunk(namespace.dimension(),Math.floorDiv(pos.getX(),16),Math.floorDiv(pos.getZ(),16)).filter(c->c.contains(pos.getX(),pos.getY(),pos.getZ()));
        }
        public Optional<BlockState> block(BlockPos pos){return data(pos).map(c->Block.BLOCK_STATE_REGISTRY.byId(c.stateId(pos.getX(),pos.getY(),pos.getZ())));}
        public Optional<Holder.Reference<Biome>> biome(BlockPos pos){return data(pos).flatMap(c->Minecraft.getInstance().getConnection().registryAccess().lookupOrThrow(Registries.BIOME).get(c.biomeId(pos.getX(),pos.getY(),pos.getZ())));}
        public OptionalInt light(boolean sky,BlockPos pos){var c=data(pos);return c.isEmpty()?OptionalInt.empty():OptionalInt.of(c.get().light(sky,pos.getX(),pos.getY(),pos.getZ()));}
        public Optional<CompoundTag> blockEntityTag(BlockPos pos){
            var c=data(pos);if(c.isEmpty())return Optional.empty();
            for(var b:c.get().blockEntities())if(b.x()==pos.getX()&&b.y()==pos.getY()&&b.z()==pos.getZ()){
                try{return Optional.of(readTag(b.tag()));}catch(IOException|RuntimeException e){CACHE.clear();return Optional.empty();}
            }
            return Optional.empty();
        }
        public Optional<Map<String,long[]>> heightmaps(int x,int z){return data(new BlockPos(x*16,scope.minY(),z*16)).map(RemoteChunkData::heightmaps);}
    }
    public static Optional<View> view(){
        var mc=Minecraft.getInstance();var scope=CACHE.scope();
        if(scope.isPresent()&&(mc.level==null||!mc.level.dimension().identifier().toString().equals(scope.get().sourceDimension()))){CACHE.clear();return Optional.empty();}
        return scope.map(View::new);
    }
    public static RemoteWorldCache cache(){return CACHE;}
    public static String metrics(){return "packets="+packets+" bytes="+wireBytes+" rejected="+rejected+" commits="+commits+" decode_ns="+decodeNanos+" peak_decode_ns="+peakDecodeNanos+" cache_bytes="+CACHE.cacheBytes()+" peak_cache_bytes="+CACHE.peakCacheBytes()+" staging_bytes="+CACHE.stagingBytes()+" peak_staging_bytes="+CACHE.peakStagingBytes();}
    public static void register(){
        try{var p=new Properties();Path file=Path.of("config/gaslight-native.properties");if(Files.exists(file))try(var in=Files.newInputStream(file)){p.load(in);}preview=Boolean.parseBoolean(p.getProperty("previewExperimental","false"));}catch(IOException e){preview=false;}
        ClientPlayNetworking.registerGlobalReceiver(RemoteWorldPayload.TYPE,(payload,context)->receive(payload.bytes()));
        ClientPlayConnectionEvents.JOIN.register((handler,sender,client)->{CACHE.disconnect();sourceDimension=null;});
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->{System.out.println("[GASLIGHT remote client] DISCONNECT "+metrics());CACHE.disconnect();sourceDimension=null;});
        ClientTickEvents.END_CLIENT_TICK.register(client->{
            if(client.level==null||client.player==null){CACHE.clear();return;}
            String actual=client.level.dimension().identifier().toString();
            if(sourceDimension!=null&&!sourceDimension.equals(actual)){CACHE.clear();System.out.println("[GASLIGHT remote client] DIMENSION_CLEAR");}
            sourceDimension=actual;CACHE.expire();
        });
    }
    private static void ack(Packet packet){
        try{if(ClientPlayNetworking.canSend(RemoteWorldPayload.Reply.TYPE))ClientPlayNetworking.send(new RemoteWorldPayload.Reply(encode(packet)));}catch(IOException|RuntimeException e){CACHE.clear();}
    }
    public static void receive(byte[] wire){
        Minecraft mc=Minecraft.getInstance();if(mc.player==null||mc.level==null||mc.getConnection()==null)return;
        Scope prior=CACHE.scope().orElse(null);long started=System.nanoTime();packets++;wireBytes+=wire.length+64;
        try{
            Packet packet=decode(wire);
            if(packet.kind()==Kind.OPEN){
                var s=packet.scope();var type=mc.getConnection().registryAccess().lookupOrThrow(Registries.DIMENSION_TYPE).get(net.minecraft.resources.Identifier.parse(s.dimensionType())).orElseThrow().value();
                if(type.minY()!=s.minY()||type.height()!=s.sections()*16||mc.getConnection().levels().stream().noneMatch(k->k.identifier().toString().equals(s.destinationDimension())))
                    throw new IllegalArgumentException("unknown/mismatched destination dimension");
            }
            boolean accepted=CACHE.accept(packet,mc.player.getUUID(),mc.level.dimension().identifier().toString(),RemoteWorldClient::validate,RemoteWorldClient::ack);
            if(accepted&&packet.kind()==Kind.OPEN)System.out.println("[GASLIGHT remote client] OPEN epoch="+packet.epoch()+" destination="+packet.scope().destinationDimension());
            if(accepted&&packet.kind()==Kind.CLOSE)System.out.println("[GASLIGHT remote client] CLOSE "+metrics());
        }catch(IOException|RuntimeException e){
            rejected++;CACHE.clear();if(prior!=null)ack(control(Kind.RESYNC,prior,prior.centerX(),prior.centerZ(),0));
            if(rejected<=5||rejected%100==0)System.err.println("[GASLIGHT remote client] REFUSED "+e.getMessage());
        }finally{long cost=System.nanoTime()-started;decodeNanos+=cost;peakDecodeNanos=Math.max(peakDecodeNanos,cost);}
    }
    private static CompoundTag readTag(byte[] bytes)throws IOException{
        var in=new DataInputStream(new ByteArrayInputStream(bytes));CompoundTag tag=NbtIo.read(in,NbtAccounter.create(65_536));if(in.available()!=0)throw new IOException("trailing NBT");return tag;
    }
    private static void validate(RemoteChunkData data){
        var mc=Minecraft.getInstance();var biomes=mc.getConnection().registryAccess().lookupOrThrow(Registries.BIOME);
        for(int y=data.minY();y<data.minY()+data.sections()*16;y++)for(int z=0;z<16;z++)for(int x=0;x<16;x++){
            int id=data.stateId(data.x()*16+x,y,data.z()*16+z);if(id>=Block.BLOCK_STATE_REGISTRY.size()||Block.BLOCK_STATE_REGISTRY.byId(id)==null)throw new IllegalArgumentException("unknown block state");
        }
        for(int y=data.minY();y<data.minY()+data.sections()*16;y+=4)for(int z=0;z<16;z+=4)for(int x=0;x<16;x+=4)
            if(biomes.get(data.biomeId(data.x()*16+x,y,data.z()*16+z)).isEmpty())throw new IllegalArgumentException("unknown biome");
        for(var be:data.blockEntities()){
            if(!BuiltInRegistries.BLOCK_ENTITY_TYPE.containsKey(net.minecraft.resources.Identifier.parse(be.type())))throw new IllegalArgumentException("unknown block entity type");
            try{readTag(be.tag());}catch(IOException e){throw new IllegalArgumentException("malformed block entity NBT",e);}
        }
        commits++;System.out.println("[GASLIGHT remote client] COMMIT chunk="+data.x()+","+data.z()+" resident_bytes="+data.residentBytes()+" block_entities="+data.blockEntities().size());
    }
    /** Retained CPU diagnostic consumes genuine synchronized chunks. Not a GPU portal. */
    public static Optional<PortalPreviewCore.Published> preview(){
        var mc=Minecraft.getInstance();var opt=view();if(!preview||opt.isEmpty()||mc.player==null)return Optional.empty();var view=opt.get();
        var center=view.scope.passage().mapPosition(new NativePortal.V(mc.player.getX(),mc.player.getY(),mc.player.getZ()));
        int x=(int)Math.floor(center.x())-16,y=(int)Math.floor(center.y())-16,z=(int)Math.floor(center.z())-16;
        if(y<view.scope.minY()||(long)y+32>view.scope.minY()+view.scope.sections()*16)return Optional.empty();
        var scene=PortalPreviewCore.capture(new PortalPreviewCore.Box(x,y,z,32,32,32),(bx,by,bz)->{
            var pos=new BlockPos(bx,by,bz);var state=view.block(pos);var light=view.light(false,pos);var sky=view.light(true,pos);
            if(state.isEmpty()||light.isEmpty()||sky.isEmpty())return PortalPreviewCore.UNKNOWN;if(state.get().isAir())return 0;
            int hash=Block.BLOCK_STATE_REGISTRY.getId(state.get())*0x45d9f3b;return 0xff000000|((70+(hash&127))<<16)|((70+((hash>>>8)&127))<<8)|(70+((hash>>>16)&127));
        });
        return scene.map(s->new PortalPreviewCore.Published(view.scope.owner(),view.scope.passage().generation(),view.scope.passage(),s,0));
    }
}
