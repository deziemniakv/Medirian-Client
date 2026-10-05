package dev.meridian.mc1_21_8;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Raw plugin-channel payloads Meridian sends and receives. Without Fabric API, unknown payload types
 * reach the client without their data, so these types are added to vanilla's custom payload codecs
 * (ClientboundCustomPayloadPacketMixin, ServerboundCustomPayloadPacketMixin).
 */
public record MeridianPayload(CustomPacketPayload.Type<MeridianPayload> type, byte[] data) implements CustomPacketPayload {

    /** Server → client: dev.meridian.policy.ServerPolicy. */
    public static final CustomPacketPayload.Type<MeridianPayload> POLICY = type("meridian", "policy");
    /** Client → server: Meridian's version. */
    public static final CustomPacketPayload.Type<MeridianPayload> HELLO = type("meridian", "hello");
    /** Client → server: plugin channels the client listens on (NUL-separated). */
    public static final CustomPacketPayload.Type<MeridianPayload> REGISTER = type("minecraft", "register");

    private static final int MAX_SIZE = 32 * 1024;

    private static CustomPacketPayload.Type<MeridianPayload> type(String namespace, String path) {
        return new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(namespace, path));
    }

    /** The payload is the rest of the packet, as is. */
    public static StreamCodec<FriendlyByteBuf, MeridianPayload> codec(CustomPacketPayload.Type<MeridianPayload> type) {
        return StreamCodec.of((buf, payload) -> buf.writeBytes(payload.data()), buf -> {
            int size = buf.readableBytes();
            if (size > MAX_SIZE) {
                throw new IllegalArgumentException("Meridian payload too large: " + size);
            }
            byte[] data = new byte[size];
            buf.readBytes(data);
            return new MeridianPayload(type, data);
        });
    }
}
