package dev.meridian.mc1_21_8.mixin;

import dev.meridian.mc1_21_8.MeridianPayload;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;

/** Server policies: the gameplay codec decodes meridian:policy instead of discarding its data. */
@Mixin(ClientboundCustomPayloadPacket.class)
public abstract class ClientboundCustomPayloadPacketMixin {

    /** The lambda that fills GAMEPLAY_STREAM_CODEC's list of known payload types. */
    @Inject(method = "method_58270", at = @At("TAIL"))
    private static void meridian$types(ArrayList<CustomPacketPayload.TypeAndCodec<? super RegistryFriendlyByteBuf, ?>> types, CallbackInfo ci) {
        types.add(new CustomPacketPayload.TypeAndCodec<>(MeridianPayload.POLICY, MeridianPayload.codec(MeridianPayload.POLICY)));
    }
}
