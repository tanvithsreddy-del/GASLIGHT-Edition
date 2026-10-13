package dev.kondalabs.gaslight.provider;
import java.util.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Connection-authenticated, session/generation/dimension-fenced camera telemetry.
 * ADVISORY ONLY: receiving a prior frame never authorizes physical publication. */
public final class CameraTelemetry {
    public record Challenge(UUID session,long generation) implements CustomPacketPayload {
        public static final Type<Challenge> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("gaslight_provider","camera_challenge"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Challenge> CODEC=StreamCodec.of((b,p)->{b.writeUUID(p.session);b.writeLong(p.generation);},b->new Challenge(b.readUUID(),b.readLong()));
        public Type<Challenge> type(){return TYPE;}
    }
    public record Observation(UUID session,long generation,long frame,String dimension,FrameClip clip,boolean supported) implements CustomPacketPayload {
        public static final Type<Observation> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("gaslight_provider","camera_observation"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Observation> CODEC=StreamCodec.of((b,p)->{
            b.writeUUID(p.session);b.writeLong(p.generation);b.writeLong(p.frame);b.writeUtf(p.dimension,128);b.writeDouble(p.clip.x());b.writeDouble(p.clip.y());b.writeDouble(p.clip.z());for(float f:p.clip.matrix())b.writeFloat(f);b.writeBoolean(p.supported);
        },b->{UUID s=b.readUUID();long g=b.readLong(),f=b.readLong();String d=b.readUtf(128);double x=b.readDouble(),y=b.readDouble(),z=b.readDouble();float[] m=new float[16];for(int i=0;i<16;i++)m[i]=b.readFloat();return new Observation(s,g,f,d,new FrameClip(x,y,z,m),b.readBoolean());});
        public Type<Observation> type(){return TYPE;}
    }
    static final class Session {
        final UUID nonce=UUID.randomUUID();final long generation;final String dimension;
        long sequence=-1,lastReceived,window;int packets,accepted,rejected;Observation latest;
        Session(long generation,String dimension){this.generation=generation;this.dimension=dimension;}
        boolean accept(Observation p,double playerX,double playerY,double playerZ,long now){
            if(now-window>1_000_000_000L){window=now;packets=0;}
            double distance=Math.pow(p.clip.x()-playerX,2)+Math.pow(p.clip.y()-playerY,2)+Math.pow(p.clip.z()-playerZ,2);
            if(++packets>12||!nonce.equals(p.session)||generation!=p.generation||!dimension.equals(p.dimension)||p.frame<=sequence||!p.supported||distance>100){rejected++;latest=null;return false;}
            sequence=p.frame;lastReceived=now;latest=p;accepted++;return true;
        }
        String status(long now){return "frame="+sequence+",accepted="+accepted+",rejected="+rejected+",fresh="+(latest!=null&&now-lastReceived<300_000_000L)+",advisoryOnly=true";}
    }
    private static final Map<net.minecraft.server.MinecraftServer,Map<UUID,Session>> servers=new IdentityHashMap<>();
    private static Map<UUID,Session> sessions(net.minecraft.server.MinecraftServer server){return servers.computeIfAbsent(server,ignored->new HashMap<>());}
    public static String status(net.minecraft.server.MinecraftServer server,UUID player){Session s=sessions(server).get(player);return s==null?"no camera session":s.status(System.nanoTime());}
    static void initialize(){
        PayloadTypeRegistry.playS2C().register(Challenge.TYPE,Challenge.CODEC);PayloadTypeRegistry.playC2S().register(Observation.TYPE,Observation.CODEC);
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->sessions(server).remove(handler.player.getUUID()));
        ServerTickEvents.END_SERVER_TICK.register(server->{
            for(var player:server.getPlayerList().getPlayers()){
                var service=GaslightProvider.service(server);if(service==null)continue;
                long generation=service.status().generation();String dimension=player.level().dimension().location().toString();
                Session s=sessions(server).get(player.getUUID());
                if((s==null||s.generation!=generation||!s.dimension.equals(dimension))&&ServerPlayNetworking.canSend(player,Challenge.TYPE)){
                    s=new Session(generation,dimension);sessions(server).put(player.getUUID(),s);ServerPlayNetworking.send(player,new Challenge(s.nonce,generation));
                }
            }
        });
        ServerPlayNetworking.registerGlobalReceiver(Observation.TYPE,(packet,context)->{
            var player=context.player();Session s=sessions(context.server()).get(player.getUUID());if(s!=null&&s.accept(packet,player.getX(),player.getEyeY(),player.getZ(),System.nanoTime())&&s.accepted==1)GaslightProvider.LOG.info("GASLIGHT_CAMERA_RECEIVED {} {}",player.getUUID(),s.status(System.nanoTime()));
        });
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(servers::remove);
    }
}
