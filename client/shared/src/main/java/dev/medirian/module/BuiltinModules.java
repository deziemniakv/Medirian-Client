package dev.medirian.module;

import dev.medirian.module.impl.combat.ComboModule;
import dev.medirian.module.impl.combat.CpsModule;
import dev.medirian.module.impl.combat.HealthTagsModule;
import dev.medirian.module.impl.combat.HitColorModule;
import dev.medirian.module.impl.combat.ReachModule;
import dev.medirian.module.impl.combat.TargetHudModule;
import dev.medirian.module.impl.hud.ClockModule;
import dev.medirian.module.impl.hud.FpsModule;
import dev.medirian.module.impl.hud.KeystrokesModule;
import dev.medirian.module.impl.hud.PingModule;
import dev.medirian.module.impl.hud.SessionInfoModule;
import dev.medirian.module.impl.hud.SpeedModule;
import dev.medirian.module.impl.hud.StopwatchModule;
import dev.medirian.module.impl.misc.AutoGgModule;
import dev.medirian.module.impl.misc.ChatModule;
import dev.medirian.module.impl.misc.ScreenshotModule;
import dev.medirian.module.impl.misc.ServerInfoModule;
import dev.medirian.module.impl.movement.FreelookModule;
import dev.medirian.module.impl.movement.ToggleSneakModule;
import dev.medirian.module.impl.movement.ToggleSprintModule;
import dev.medirian.module.impl.movement.ZoomModule;
import dev.medirian.module.impl.performance.DynamicFpsModule;
import dev.medirian.module.impl.performance.EntityCullingModule;
import dev.medirian.module.impl.performance.FpsGraphModule;
import dev.medirian.module.impl.performance.MemoryModule;
import dev.medirian.module.impl.performance.ParticleControlModule;
import dev.medirian.module.impl.player.ArmorStatusModule;
import dev.medirian.module.impl.player.CoordinatesModule;
import dev.medirian.module.impl.player.PotionEffectsModule;
import dev.medirian.module.impl.render.BlockOverlayModule;
import dev.medirian.module.impl.render.CrosshairModule;
import dev.medirian.module.impl.render.FireOverlayModule;
import dev.medirian.module.impl.render.FullbrightModule;
import dev.medirian.module.impl.render.HurtCameraModule;
import dev.medirian.module.impl.render.ItemPhysicsModule;
import dev.medirian.module.impl.render.ScoreboardModule;
import dev.medirian.module.impl.render.TimeChangerModule;
import dev.medirian.module.impl.render.WeatherChangerModule;
import dev.medirian.module.impl.world.BiomeModule;
import dev.medirian.module.impl.world.DirectionModule;
import dev.medirian.module.impl.world.WaypointsModule;

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
