package dev.medirian.mc1_8_9.mixin;

import dev.medirian.platform.Hooks;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/** Particle Control: drops new particles according to the multiplier and cap. */
@Mixin(ParticleManager.class)
public abstract class ParticleManagerMixin {

    @Shadow
    private List<Particle>[][] particles;

    @Inject(method = "addParticle(Lnet/minecraft/client/particle/Particle;)V", at = @At("HEAD"), cancellable = true)
    private void medirian$add(Particle particle, CallbackInfo ci) {
        int count = 0;
        for (List<Particle>[] layer : particles) {
            for (List<Particle> list : layer) {
                count += list.size();
            }
        }
        if (!Hooks.allowParticle(count)) {
            ci.cancel();
        }
    }
}
