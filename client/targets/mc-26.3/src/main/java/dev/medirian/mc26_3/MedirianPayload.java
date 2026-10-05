package dev.medirian.mc26_3;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Raw plugin-channel payloads Medirian sends and receives. Without Fabric API, unknown payload types
 * reach the client without their data, so these types are added to vanilla's custom payload codecs
 * (ClientboundCustomPayloadPacketMixin, ServerboundCustomPayloadPacketMixin).
 */
public record MedirianPayload(CustomPacketPayload.Type<MedirianPayload> type, byte[] data) implements CustomPacketPayload {

    /** Server → client: dev.medirian.policy.ServerPolicy. */
    public static final CustomPacketPayload.Type<MedirianPayload> POLICY = type("medirian", "policy");
    /** Client → server: Medirian's version. */
    public static final CustomPacketPayload.Type<MedirianPayload> HELLO = type("medirian", "hello");
    /** Client → server: plugin channels the client listens on (NUL-separated). */
    public static final CustomPacketPayload.Type<MedirianPayload> REGISTER = type("minecraft", "register");

    private static final int MAX_SIZE = 32 * 1024;

    private static CustomPacketPayload.Type<MedirianPayload> type(String namespace, String path) {
        return new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(namespace, path));
    }

    /** The payload is the rest of the packet, as is. */
    public static StreamCodec<FriendlyByteBuf, MedirianPayload> codec(CustomPacketPayload.Type<MedirianPayload> type) {
        return StreamCodec.of((buf, payload) -> buf.writeBytes(payload.data()), buf -> {
            int size = buf.readableBytes();
            if (size > MAX_SIZE) {
                throw new IllegalArgumentException("Medirian payload too large: " + size);
            }
            byte[] data = new byte[size];
            buf.readBytes(data);
            return new MedirianPayload(type, data);
        });
    }
}
