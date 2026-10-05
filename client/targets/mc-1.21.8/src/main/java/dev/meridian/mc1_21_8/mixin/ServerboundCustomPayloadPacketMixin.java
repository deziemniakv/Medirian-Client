package dev.meridian.mc1_21_8.mixin;

import dev.meridian.mc1_21_8.MeridianPayload;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;

/** Server policies: the codec can encode minecraft:register and meridian:hello. */
@Mixin(ServerboundCustomPayloadPacket.class)
public abstract class ServerboundCustomPayloadPacketMixin {

    /** The lambda that fills STREAM_CODEC's list of known payload types. */
    @Inject(method = "method_58271", at = @At("TAIL"))
    private static void meridian$types(ArrayList<CustomPacketPayload.TypeAndCodec<? super FriendlyByteBuf, ?>> types, CallbackInfo ci) {
        types.add(new CustomPacketPayload.TypeAndCodec<>(MeridianPayload.REGISTER, MeridianPayload.codec(MeridianPayload.REGISTER)));
        types.add(new CustomPacketPayload.TypeAndCodec<>(MeridianPayload.HELLO, MeridianPayload.codec(MeridianPayload.HELLO)));
    }
}
