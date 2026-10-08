package dev.medirian.mc1_21_11.mixin;

import net.minecraft.client.particle.Particle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Where a particle is (for the particle distance of Settings → Advanced → Visibility). */
@Mixin(Particle.class)
public interface ParticleAccessor {

    @Accessor("x")
    double medirian$x();

    @Accessor("y")
    double medirian$y();

    @Accessor("z")
    double medirian$z();
}
