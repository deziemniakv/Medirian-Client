package dev.medirian.mc26_3.mixin;

import dev.medirian.mc26_3.MedirianPayload;
import dev.medirian.platform.Hooks;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Server policies: hands medirian:policy payloads to Medirian (already on the client thread here). */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {

    @Inject(method = "handleCustomPayload(Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;)V",
            at = @At("HEAD"), cancellable = true)
    private void medirian$payload(CustomPacketPayload payload, CallbackInfo ci) {
        if (payload instanceof MedirianPayload medirian && medirian.type() == MedirianPayload.POLICY) {
            Hooks.serverPolicy(medirian.data());
            ci.cancel();
        }
    }
}
