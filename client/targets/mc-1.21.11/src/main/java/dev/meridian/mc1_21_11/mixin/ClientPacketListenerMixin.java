package dev.meridian.mc1_21_11.mixin;

import dev.meridian.mc1_21_11.MeridianPayload;
import dev.meridian.platform.Hooks;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Server policies: hands meridian:policy payloads to Meridian (already on the client thread here). */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {

    @Inject(method = "handleCustomPayload(Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;)V",
            at = @At("HEAD"), cancellable = true)
    private void meridian$payload(CustomPacketPayload payload, CallbackInfo ci) {
        if (payload instanceof MeridianPayload meridian && meridian.type() == MeridianPayload.POLICY) {
            Hooks.serverPolicy(meridian.data());
            ci.cancel();
        }
    }
}
