package dev.meridian.perf;

/**
 * Performance modes. Every value maps to concrete, measurable settings applied by
 * {@link PerformanceManager#applyProfile}: particle multiplier/cap, entity render distance,
 * background frame cap and UI animations. Nothing here is cosmetic.
 */
public enum PerformanceProfile {
    /** Maximum FPS: aggressive particle and entity limits, low background FPS, no UI animations. */
    PERFORMANCE(true, 0.35, 1000, true, 48, 96, 15, false),
    /** Sensible limits that are hard to notice while playing. */
    BALANCED(true, 0.75, 2500, true, 80, 160, 30, true),
    /** Vanilla visuals; only the background FPS cap stays active. */
    QUALITY(false, 1.0, 4000, false, 128, 256, 60, true);

    public final boolean particleControl;
    public final double particleMultiplier;
    public final int maxParticles;
    public final boolean entityCulling;
    public final int entityDistance;
    public final int playerDistance;
    public final int backgroundFps;
    public final boolean animations;

    PerformanceProfile(boolean particleControl, double particleMultiplier, int maxParticles, boolean entityCulling,
                       int entityDistance, int playerDistance, int backgroundFps, boolean animations) {
        this.particleControl = particleControl;
        this.particleMultiplier = particleMultiplier;
        this.maxParticles = maxParticles;
        this.entityCulling = entityCulling;
        this.entityDistance = entityDistance;
        this.playerDistance = playerDistance;
        this.backgroundFps = backgroundFps;
        this.animations = animations;
    }
}
