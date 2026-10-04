package dev.meridian.module;

import dev.meridian.module.impl.combat.ComboModule;
import dev.meridian.module.impl.combat.CpsModule;
import dev.meridian.module.impl.combat.HealthTagsModule;
import dev.meridian.module.impl.combat.HitColorModule;
import dev.meridian.module.impl.combat.ReachModule;
import dev.meridian.module.impl.combat.TargetHudModule;
import dev.meridian.module.impl.hud.ClockModule;
import dev.meridian.module.impl.hud.FpsModule;
import dev.meridian.module.impl.hud.KeystrokesModule;
import dev.meridian.module.impl.hud.PingModule;
import dev.meridian.module.impl.hud.SessionInfoModule;
import dev.meridian.module.impl.hud.SpeedModule;
import dev.meridian.module.impl.hud.StopwatchModule;
import dev.meridian.module.impl.misc.AutoGgModule;
import dev.meridian.module.impl.misc.ChatModule;
import dev.meridian.module.impl.misc.ScreenshotModule;
import dev.meridian.module.impl.misc.ServerInfoModule;
import dev.meridian.module.impl.movement.FreelookModule;
import dev.meridian.module.impl.movement.ToggleSneakModule;
import dev.meridian.module.impl.movement.ToggleSprintModule;
import dev.meridian.module.impl.movement.ZoomModule;
import dev.meridian.module.impl.performance.DynamicFpsModule;
import dev.meridian.module.impl.performance.EntityCullingModule;
import dev.meridian.module.impl.performance.FpsGraphModule;
import dev.meridian.module.impl.performance.MemoryModule;
import dev.meridian.module.impl.performance.ParticleControlModule;
import dev.meridian.module.impl.player.ArmorStatusModule;
import dev.meridian.module.impl.player.CoordinatesModule;
import dev.meridian.module.impl.player.PotionEffectsModule;
import dev.meridian.module.impl.render.BlockOverlayModule;
import dev.meridian.module.impl.render.CrosshairModule;
import dev.meridian.module.impl.render.FireOverlayModule;
import dev.meridian.module.impl.render.FullbrightModule;
import dev.meridian.module.impl.render.HurtCameraModule;
import dev.meridian.module.impl.render.ItemPhysicsModule;
import dev.meridian.module.impl.render.ScoreboardModule;
import dev.meridian.module.impl.render.TimeChangerModule;
import dev.meridian.module.impl.render.WeatherChangerModule;
import dev.meridian.module.impl.world.BiomeModule;
import dev.meridian.module.impl.world.DirectionModule;
import dev.meridian.module.impl.world.WaypointsModule;

/**
 * Registers every built-in module. Modules whose capabilities the running version does not
 * implement are skipped by {@link ModuleManager#register}. Order defines the default order in UI.
 */
public final class BuiltinModules {

    private BuiltinModules() {
    }

    public static void registerAll(ModuleManager modules) {
        // COMBAT
        modules.register(new CpsModule());
        modules.register(new ComboModule());
        modules.register(new ReachModule());
        modules.register(new TargetHudModule());
        modules.register(new HitColorModule());
        modules.register(new HealthTagsModule());
        // MOVEMENT
        modules.register(new ToggleSprintModule());
        modules.register(new ToggleSneakModule());
        modules.register(new ZoomModule());
        modules.register(new FreelookModule());
        // PLAYER
        modules.register(new ArmorStatusModule());
        modules.register(new PotionEffectsModule());
        modules.register(new CoordinatesModule());
        // RENDER
        modules.register(new CrosshairModule());
        modules.register(new BlockOverlayModule());
        modules.register(new HurtCameraModule());
        modules.register(new FireOverlayModule());
        modules.register(new ItemPhysicsModule());
        modules.register(new FullbrightModule());
        modules.register(new TimeChangerModule());
        modules.register(new WeatherChangerModule());
        modules.register(new ScoreboardModule());
        // WORLD
        modules.register(new DirectionModule());
        modules.register(new BiomeModule());
        modules.register(new WaypointsModule());
        // HUD
        modules.register(new FpsModule());
        modules.register(new KeystrokesModule());
        modules.register(new PingModule());
        modules.register(new SpeedModule());
        modules.register(new ClockModule());
        modules.register(new StopwatchModule());
        modules.register(new SessionInfoModule());
        // PERFORMANCE
        modules.register(new DynamicFpsModule());
        modules.register(new ParticleControlModule());
        modules.register(new EntityCullingModule());
        modules.register(new MemoryModule());
        modules.register(new FpsGraphModule());
        // MISC
        modules.register(new ChatModule());
        modules.register(new ScreenshotModule());
        modules.register(new AutoGgModule());
        modules.register(new ServerInfoModule());
    }
}
