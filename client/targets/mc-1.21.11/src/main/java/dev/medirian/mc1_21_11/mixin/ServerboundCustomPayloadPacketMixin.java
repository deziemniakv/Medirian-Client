package dev.medirian.mc1_21_11.mixin;

import dev.medirian.mc1_21_11.MedirianPayload;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;

/** Server policies: the codec can encode minecraft:register and medirian:hello. */
@Mixin(ServerboundCustomPayloadPacket.class)
public abstract class ServerboundCustomPayloadPacketMixin {

    /** The lambda that fills STREAM_CODEC's list of known payload types. */
    @Inject(method = "method_58271", at = @At("TAIL"))
    private static void medirian$types(ArrayList<CustomPacketPayload.TypeAndCodec<? super FriendlyByteBuf, ?>> types, CallbackInfo ci) {
        types.add(new CustomPacketPayload.TypeAndCodec<>(MedirianPayload.REGISTER, MedirianPayload.codec(MedirianPayload.REGISTER)));
        types.add(new CustomPacketPayload.TypeAndCodec<>(MedirianPayload.HELLO, MedirianPayload.codec(MedirianPayload.HELLO)));
    }
}
