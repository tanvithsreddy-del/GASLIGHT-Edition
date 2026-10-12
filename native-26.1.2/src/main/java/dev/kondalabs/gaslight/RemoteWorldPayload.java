package dev.kondalabs.gaslight;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Outer allocation bounds are enforced before reading the custom wire. */
public record RemoteWorldPayload(byte[] bytes) implements CustomPacketPayload {
    public static final Type<RemoteWorldPayload> TYPE=new Type<>(Identifier.fromNamespaceAndPath("gaslight_native","remote_world_v1"));
    public static final StreamCodec<RegistryFriendlyByteBuf,RemoteWorldPayload> CODEC=StreamCodec.of(
        (b,p)->b.writeByteArray(p.bytes),b->new RemoteWorldPayload(b.readByteArray(RemoteWorldWire.MAX_PACKET)));
    public RemoteWorldPayload {if(bytes==null||bytes.length>RemoteWorldWire.MAX_PACKET)throw new IllegalArgumentException("remote packet budget");bytes=bytes.clone();}
    @Override public byte[] bytes(){return bytes.clone();}
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    public record Reply(byte[] bytes) implements CustomPacketPayload {
        public static final Type<Reply> TYPE=new Type<>(Identifier.fromNamespaceAndPath("gaslight_native","remote_world_reply_v1"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Reply> CODEC=StreamCodec.of(
            (b,p)->b.writeByteArray(p.bytes),b->new Reply(b.readByteArray(256)));
        public Reply {if(bytes==null||bytes.length>256)throw new IllegalArgumentException("reply budget");bytes=bytes.clone();}
        @Override public byte[] bytes(){return bytes.clone();}
        @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    public static void register(){PayloadTypeRegistry.clientboundPlay().register(TYPE,CODEC);PayloadTypeRegistry.serverboundPlay().register(Reply.TYPE,Reply.CODEC);}
}
