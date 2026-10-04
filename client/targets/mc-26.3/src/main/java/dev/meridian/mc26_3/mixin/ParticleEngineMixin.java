package dev.meridian.mc26_3.mixin;

import dev.meridian.platform.Hooks;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleGroup;
import net.minecraft.client.particle.ParticleRenderType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.Queue;

/** Particle Control: drops new particles according to the multiplier and cap. */
@Mixin(ParticleEngine.class)
public abstract class ParticleEngineMixin {

    @Shadow
    @Final
    private Map<ParticleRenderType, ParticleGroup<?>> particles;

    @Shadow
    @Final
    private Queue<Particle> particlesToAdd;

    @Inject(method = "add", at = @At("HEAD"), cancellable = true)
    private void meridian$add(Particle particle, CallbackInfo ci) {
        int count = particlesToAdd.size();
        for (ParticleGroup<?> group : particles.values()) {
            count += group.size();
        }
        if (!Hooks.allowParticle(count)) {
            ci.cancel();
        }
    }
}
