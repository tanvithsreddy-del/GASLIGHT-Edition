package dev.kondalabs.gaslight;

import java.util.Objects;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Fabric 26.1.2 clientbound custom payload. All decompression is handled by
 * PortalSnapshotWire after the Minecraft packet's bounded byte[] has been read.
 * This class has no client-only references and can load on dedicated servers.
 */
public record PortalSnapshotPayload(byte[] bytes) implements CustomPacketPayload {
    public static final Type<PortalSnapshotPayload> TYPE=new Type<>(
        Identifier.fromNamespaceAndPath("gaslight_native","phase2_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf,PortalSnapshotPayload> CODEC=
        StreamCodec.of((buf,packet)->buf.writeByteArray(packet.bytes()),
                       buf->new PortalSnapshotPayload(buf.readByteArray(PortalSnapshotWire.MAX_BYTES)));
    public PortalSnapshotPayload {
        Objects.requireNonNull(bytes);
        if(bytes.length>PortalSnapshotWire.MAX_BYTES)throw new IllegalArgumentException("wire budget");
        bytes=bytes.clone();
    }
    @Override public byte[] bytes(){return bytes.clone();}
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    public static void register(){PayloadTypeRegistry.clientboundPlay().register(TYPE,CODEC);}
}
