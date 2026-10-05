package dev.medirian.mc1_8_9.mixin;

import dev.medirian.platform.Hooks;
import dev.medirian.policy.ServerPolicy;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.CustomPayloadS2CPacket;
import net.minecraft.util.PacketByteBuf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Server policies: reads medirian:policy plugin messages. */
@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerMixin {

    /** After forceMainThread: the handler first runs on the network thread and is aborted there. */
    @Inject(method = "onCustomPayload", cancellable = true, at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lnet/minecraft/network/NetworkThreadUtils;forceMainThread(Lnet/minecraft/network/Packet;Lnet/minecraft/network/listener/PacketListener;Lnet/minecraft/util/ThreadExecutor;)V"))
    private void medirian$payload(CustomPayloadS2CPacket packet, CallbackInfo ci) {
        if (!ServerPolicy.CHANNEL.equals(packet.getChannel())) {
            return;
        }
        PacketByteBuf buf = packet.getPayload();
        byte[] data = new byte[Math.min(buf.readableBytes(), ServerPolicy.MAX_SIZE + 1)];
        buf.readBytes(data);
        Hooks.serverPolicy(data);
        ci.cancel();
    }
}
