package dev.kondalabs.gaslight.provider;

import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

final class SceneProtocol {
    private static final java.util.Map<UUID,Caps> CAPABILITIES=new java.util.concurrent.ConcurrentHashMap<>();
    static boolean supported(net.minecraft.server.level.ServerPlayer player){var cap=CAPABILITIES.get(player.getUUID());return cap!=null&&cap.protocol()==1&&cap.vanillaRenderer()&&cap.renderDistance()>=2&&cap.renderDistance()<=4;}
    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> type(String name) {
        return new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("gaslight_provider",name));
    }
    static void vector(RegistryFriendlyByteBuf b,Vec3 v){b.writeDouble(v.x);b.writeDouble(v.y);b.writeDouble(v.z);}
    static Vec3 vector(RegistryFriendlyByteBuf b){return new Vec3(b.readDouble(),b.readDouble(),b.readDouble());}
    record Caps(int protocol,boolean vanillaRenderer,int renderDistance) implements CustomPacketPayload {
        static final Type<Caps> TYPE=SceneProtocol.type("scene_capabilities");
        static final StreamCodec<RegistryFriendlyByteBuf,Caps> CODEC=StreamCodec.of((b,p)->{b.writeInt(p.protocol);b.writeBoolean(p.vanillaRenderer);b.writeInt(p.renderDistance);},b->new Caps(b.readInt(),b.readBoolean(),b.readInt()));
        Caps{if(protocol<0||protocol>16||renderDistance<0||renderDistance>128)throw new IllegalArgumentException("capability bounds");}
        @Override public Type<Caps> type(){return TYPE;}
    }
    record Offer(UUID session,long generation,Vec3 origin,Vec3 destination,boolean xPlane,int sign,int minY,int maxY,UUID first,UUID second,Vec3 previous,Vec3 previousDestination,boolean previousXPlane,int previousSign,String seamDigest) implements CustomPacketPayload {
        static final Type<Offer> TYPE=SceneProtocol.type("scene_offer");
        static final StreamCodec<RegistryFriendlyByteBuf,Offer> CODEC=StreamCodec.of((b,p)->{
            b.writeUUID(p.session);b.writeLong(p.generation);vector(b,p.origin);vector(b,p.destination);b.writeBoolean(p.xPlane);b.writeInt(p.sign);b.writeInt(p.minY);b.writeInt(p.maxY);b.writeUUID(p.first);b.writeUUID(p.second);b.writeBoolean(p.previous!=null);if(p.previous!=null){vector(b,p.previous);vector(b,p.previousDestination);}b.writeBoolean(p.previousXPlane);b.writeInt(p.previousSign);b.writeUtf(p.seamDigest,64);
        },b->{UUID s=b.readUUID();long g=b.readLong();Vec3 a=vector(b),d=vector(b);boolean x=b.readBoolean();int sign=b.readInt(),lo=b.readInt(),hi=b.readInt();UUID p=b.readUUID(),q=b.readUUID();boolean had=b.readBoolean();Vec3 old=had?vector(b):null,oldDest=had?vector(b):null;return new Offer(s,g,a,d,x,sign,lo,hi,p,q,old,oldDest,b.readBoolean(),b.readInt(),b.readUtf(64));});
        Offer {
            if(session==null||generation<1||first==null||second==null||Math.abs(sign)!=1||Math.abs(previousSign)!=1||minY< -2048||maxY>2048||maxY<=minY||maxY-minY>512||seamDigest==null||!seamDigest.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("offer fence");
            for(Vec3 p:previous==null?java.util.List.of(origin,destination):java.util.List.of(origin,destination,previous,previousDestination))
                if(p==null||!Double.isFinite(p.x)||!Double.isFinite(p.y)||!Double.isFinite(p.z)||Math.abs(p.x)>29_000_000||Math.abs(p.z)>29_000_000||Math.abs(p.y)>2048)throw new IllegalArgumentException("offer coordinate");
        }
        @Override public Type<Offer> type(){return TYPE;}
        boolean hidden(FrameClip clip) {
            return halfHidden(clip,origin,xPlane,sign) && (previous==null||(planeHidden(clip,previous,previousXPlane)&&planeHidden(clip,previousDestination,previousXPlane)));
        }
        private boolean planeHidden(FrameClip clip,Vec3 p,boolean x){return clip.excludes(p.x-(x?.01:32),minY,p.z-(x?32:.01),p.x+(x?.01:32),maxY,p.z+(x?32:.01));}
        private boolean halfHidden(FrameClip clip,Vec3 p,boolean x,int direction){
            double lo=-30_000_000,hi=30_000_000;
            return x?clip.excludes(direction>0?lo:p.x,minY,lo,direction>0?p.x:hi,maxY,hi):clip.excludes(lo,minY,direction>0?lo:p.z,hi,maxY,direction>0?p.z:hi);
        }
    }
    record Ready(UUID session,long generation,int meshes,int cells,String seamDigest) implements CustomPacketPayload {
        static final Type<Ready> TYPE=SceneProtocol.type("scene_ready");
        static final StreamCodec<RegistryFriendlyByteBuf,Ready> CODEC=StreamCodec.of((b,p)->{b.writeUUID(p.session);b.writeLong(p.generation);b.writeInt(p.meshes);b.writeInt(p.cells);b.writeUtf(p.seamDigest,64);},b->new Ready(b.readUUID(),b.readLong(),b.readInt(),b.readInt(),b.readUtf(64)));
        Ready{if(session==null||generation<1||meshes<1||meshes>2048||cells<1||cells>65536||seamDigest==null||!seamDigest.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("readiness bounds");}
        @Override public Type<Ready> type(){return TYPE;}
    }
    record Selection(UUID session,long generation,long frame,FrameClip clip) implements CustomPacketPayload {
        static final Type<Selection> TYPE=SceneProtocol.type("scene_selection");
        static final StreamCodec<RegistryFriendlyByteBuf,Selection> CODEC=StreamCodec.of((b,p)->{b.writeUUID(p.session);b.writeLong(p.generation);b.writeLong(p.frame);b.writeDouble(p.clip.x());b.writeDouble(p.clip.y());b.writeDouble(p.clip.z());for(float f:p.clip.matrix())b.writeFloat(f);},b->{UUID s=b.readUUID();long g=b.readLong(),f=b.readLong();double x=b.readDouble(),y=b.readDouble(),z=b.readDouble();float[] m=new float[16];for(int i=0;i<16;i++)m[i]=b.readFloat();return new Selection(s,g,f,new FrameClip(x,y,z,m));});
        @Override public Type<Selection> type(){return TYPE;}
    }
    record Armed(UUID session,long generation) implements CustomPacketPayload {
        static final Type<Armed> TYPE=SceneProtocol.type("scene_armed");
        static final StreamCodec<RegistryFriendlyByteBuf,Armed> CODEC=StreamCodec.of((b,p)->{b.writeUUID(p.session);b.writeLong(p.generation);},b->new Armed(b.readUUID(),b.readLong()));
        @Override public Type<Armed> type(){return TYPE;}
    }
    record Cancel(UUID session,long generation) implements CustomPacketPayload {
        static final Type<Cancel> TYPE=SceneProtocol.type("scene_cancel");
        static final StreamCodec<RegistryFriendlyByteBuf,Cancel> CODEC=StreamCodec.of((b,p)->{b.writeUUID(p.session);b.writeLong(p.generation);},b->new Cancel(b.readUUID(),b.readLong()));
        @Override public Type<Cancel> type(){return TYPE;}
    }
    record Withdraw(UUID session,long generation) implements CustomPacketPayload {
        static final Type<Withdraw> TYPE=SceneProtocol.type("scene_withdraw");
        static final StreamCodec<RegistryFriendlyByteBuf,Withdraw> CODEC=StreamCodec.of((b,p)->{b.writeUUID(p.session);b.writeLong(p.generation);},b->new Withdraw(b.readUUID(),b.readLong()));
        @Override public Type<Withdraw> type(){return TYPE;}
    }
    record Ack(UUID session,long generation,long frame,boolean accepted) implements CustomPacketPayload {
        static final Type<Ack> TYPE=SceneProtocol.type("scene_ack");
        static final StreamCodec<RegistryFriendlyByteBuf,Ack> CODEC=StreamCodec.of((b,p)->{b.writeUUID(p.session);b.writeLong(p.generation);b.writeLong(p.frame);b.writeBoolean(p.accepted);},b->new Ack(b.readUUID(),b.readLong(),b.readLong(),b.readBoolean()));
        @Override public Type<Ack> type(){return TYPE;}
    }
    static void initialize(){
        PayloadTypeRegistry.playS2C().register(Offer.TYPE,Offer.CODEC);PayloadTypeRegistry.playS2C().register(Ack.TYPE,Ack.CODEC);PayloadTypeRegistry.playC2S().register(Selection.TYPE,Selection.CODEC);PayloadTypeRegistry.playC2S().register(Ready.TYPE,Ready.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Ready.TYPE,(p,c)->{var s=GaslightProvider.automatic(c.server());if(s!=null)s.clientReady(c.player(),p);});
        PayloadTypeRegistry.playC2S().register(Cancel.TYPE,Cancel.CODEC);PayloadTypeRegistry.playS2C().register(Withdraw.TYPE,Withdraw.CODEC);
        PayloadTypeRegistry.playS2C().register(Armed.TYPE,Armed.CODEC);
        PayloadTypeRegistry.playC2S().register(Caps.TYPE,Caps.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Caps.TYPE,(p,c)->{if(c.server().getPlayerList().getPlayerCount()<=4)CAPABILITIES.put(c.player().getUUID(),p);});
        ServerPlayNetworking.registerGlobalReceiver(Cancel.TYPE,(p,c)->{var s=GaslightProvider.automatic(c.server());if(s!=null)s.cancel(c.player(),p);});
        ServerPlayNetworking.registerGlobalReceiver(Selection.TYPE,(p,c)->{var s=GaslightProvider.automatic(c.server());if(s!=null)s.select(c.player(),p);});
        ServerPlayConnectionEvents.DISCONNECT.register((h,s)->s.execute(()->{CAPABILITIES.remove(h.player.getUUID());var a=GaslightProvider.automatic(s);if(a!=null)a.disconnected(h.player);}));
    }
}
