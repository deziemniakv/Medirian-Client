package dev.medirian.perf;

import dev.medirian.config.GlobalSettings;
import dev.medirian.config.ProfileSettings;
import dev.medirian.module.Module;
import dev.medirian.module.ModuleManager;
import dev.medirian.render.Anim;
import dev.medirian.setting.NumberSetting;

/**
 * Monitors client performance and applies {@link PerformanceProfile}s.
 *
 * <p>Measured: FPS / frame time / 1% low ({@link FrameStats}), JVM memory and process CPU
 * ({@link SystemStats}), world render time (when the adapter reports it) and Medirian's own HUD
 * render cost — so the client's overhead is always visible to the user.
 */
public final class PerformanceManager {

    private final FrameStats frames = new FrameStats();
    private final SystemStats system = new SystemStats();
    private final ModuleManager modules;
    private final GlobalSettings global;
    private final ProfileSettings profileSettings;

    private long worldRenderStart;
    private float worldRenderMs;
    private float hudRenderMs;

    public PerformanceManager(ModuleManager modules, GlobalSettings global, ProfileSettings profileSettings) {
        this.modules = modules;
        this.global = global;
        this.profileSettings = profileSettings;
        profileSettings.performanceProfile.onChange(this::applyProfile);
        global.animations.onChange(on -> updateAnimations());
    }

    // ----- measurements -----

    public void onFrame() {
        frames.frame(System.nanoTime());
    }

    public void onTick(long nowMs) {
        system.sample(nowMs);
    }

    public void worldRenderStart() {
        worldRenderStart = System.nanoTime();
    }

    public void worldRenderEnd() {
        if (worldRenderStart != 0) {
            worldRenderMs = ema(worldRenderMs, (System.nanoTime() - worldRenderStart) / 1_000_000f);
            worldRenderStart = 0;
        }
    }

    public void recordHudRender(long nanos) {
        hudRenderMs = ema(hudRenderMs, nanos / 1_000_000f);
    }

    private static float ema(float previous, float sample) {
        return previous == 0 ? sample : previous * 0.95f + sample * 0.05f;
    }

    public FrameStats frames() {
        return frames;
    }

    public SystemStats system() {
        return system;
    }

    /** Smoothed world render time in ms, or 0 when the adapter does not report it. */
    public float worldRenderMs() {
        return worldRenderMs;
    }

    /** Smoothed cost of Medirian's HUD per frame in ms. */
    public float hudRenderMs() {
        return hudRenderMs;
    }

    // ----- profiles -----

    public PerformanceProfile profile() {
        return profileSettings.performanceProfile.get();
    }

    /** Applies the concrete settings of {@code profile} to the performance modules. */
    public void applyProfile(PerformanceProfile profile) {
        Module particles = modules.get("particles");
        if (particles != null) {
            particles.setEnabled(profile.particleControl);
            setNumber(particles, "multiplier", profile.particleMultiplier * 100);
            setNumber(particles, "maxParticles", profile.maxParticles);
        }
        Module culling = modules.get("entityculling");
        if (culling != null) {
            culling.setEnabled(profile.entityCulling);
            setNumber(culling, "entityDistance", profile.entityDistance);
            setNumber(culling, "playerDistance", profile.playerDistance);
        }
        Module dynamicFps = modules.get("dynamicfps");
        if (dynamicFps != null) {
            dynamicFps.setEnabled(true);
            setNumber(dynamicFps, "unfocusedFps", profile.backgroundFps);
        }
        updateAnimations();
    }

    /** UI animations run only when enabled globally and by the active performance profile. */
    public void updateAnimations() {
        Anim.setEnabled(global.animations.on() && profile().animations);
    }

    private static void setNumber(Module module, String settingId, double value) {
        Object setting = module.setting(settingId);
        if (setting instanceof NumberSetting) {
            ((NumberSetting) setting).set(value);
        }
    }
}
