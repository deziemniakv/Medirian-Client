package dev.meridian.module.impl.performance;

import dev.meridian.module.Category;
import dev.meridian.module.Module;
import dev.meridian.platform.Capability;
import dev.meridian.setting.NumberSetting;

/**
 * Reduces particle spawning: a probabilistic multiplier plus a hard cap on live particles.
 * Fewer particles means less simulation and fewer draw calls in busy fights.
 */
public final class ParticleControlModule extends Module {

    private final NumberSetting multiplier;
    private final NumberSetting maxParticles;
    private long seed = System.nanoTime() | 1L;

    public ParticleControlModule() {
        super("particles", "Particle Control", Category.PERFORMANCE, "Limits how many particles are spawned.");
        requires(Capability.PARTICLE_CONTROL);
        multiplier = add(new NumberSetting("multiplier", "Particle amount", 100, 0, 100, 5).unit("%"));
        maxParticles = add(new NumberSetting("maxParticles", "Max particles", 4000, 100, 16000, 100));
    }

    public boolean allow(int currentCount) {
        if (!isEnabled()) {
            return true;
        }
        if (currentCount >= maxParticles.intValue()) {
            return false;
        }
        double amount = multiplier.doubleValue() / 100.0;
        if (amount >= 1.0) {
            return true;
        }
        return nextDouble() < amount;
    }

    /** xorshift64 — cheap and lock-free; Math.random() synchronises. */
    private double nextDouble() {
        long x = seed;
        x ^= x << 13;
        x ^= x >>> 7;
        x ^= x << 17;
        seed = x;
        return (x >>> 11) * 0x1.0p-53;
    }
}
