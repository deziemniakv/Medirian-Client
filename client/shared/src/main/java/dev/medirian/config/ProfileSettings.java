package dev.medirian.config;

import dev.medirian.perf.PerformanceProfile;
import dev.medirian.setting.BooleanSetting;
import dev.medirian.setting.ModeSetting;
import dev.medirian.setting.NumberSetting;
import dev.medirian.setting.Setting;
import dev.medirian.setting.SettingsOwner;

import java.util.ArrayList;
import java.util.List;

/**
 * Visual, visibility and performance settings stored inside each configuration profile (so they
 * travel with profile codes). Minecraft's own options live in its options.txt instead (see
 * {@link dev.medirian.platform.VanillaOptions}).
 */
public final class ProfileSettings implements SettingsOwner {

    /** How HUD widgets are drawn. */
    public enum HudLook { GLASS, CLASSIC }

    /** The fog at the edge of the render distance (fog under water, in lava or from effects stays). */
    public enum FogMode { VANILLA, REDUCED, OFF }

    /** Visibility distances run from 8 blocks to this, which means "no limit". */
    public static final int NO_LIMIT = 256;

    private final List<Setting<?>> settings = new ArrayList<Setting<?>>();
    private Runnable changeListener;

    public final NumberSetting hudScale = add(new NumberSetting("hudScale", "HUD scale", 1.0, 0.5, 2.0, 0.05).unit("x")
            .description("Size of every HUD widget together. Each widget also has its own scale in the HUD editor."));
    public final ModeSetting<PerformanceProfile> performanceProfile = add(new ModeSetting<PerformanceProfile>(
            "performanceProfile", "Performance mode", PerformanceProfile.BALANCED)
            .description("Applies real optimisations: particle limits, entity render distance, background FPS cap and UI animations."));

    // ------------------------------------------------------------------ HUD look

    public final ModeSetting<HudLook> hudLook = add(new ModeSetting<HudLook>("hudLook", "HUD style", HudLook.GLASS)
            .description("Liquid Glass: translucent widgets with a soft blur of the world behind them. Classic: flat dark boxes."));
    public final NumberSetting glassOpacity = add(new NumberSetting("glassOpacity", "Glass opacity", 55, 10, 95, 5).unit("%")
            .description("How solid the glass is. Higher hides more of the world and is easier to read on bright scenes.")
            .visibleWhen(this::glass));
    public final NumberSetting glassBlur = add(new NumberSetting("glassBlur", "Blur", 50, 0, 100, 25).unit("%")
            .minLabel("setting.off", "Off")
            .description("Blurs the world behind widgets. Costs one small blur of the screen per frame while the HUD is visible; the lowest value turns it off.")
            .visibleWhen(this::glass));
    public final NumberSetting glassBorder = add(new NumberSetting("glassBorder", "Border", 35, 0, 100, 5).unit("%")
            .minLabel("setting.off", "Off")
            .description("Strength of the thin light edge around each widget.")
            .visibleWhen(this::glass));
    public final NumberSetting hudRadius = add(new NumberSetting("hudRadius", "Corner radius", 4, 0, 8, 1).unit("px")
            .description("Roundness of widget corners."));
    public final NumberSetting hudPadding = add(new NumberSetting("hudPadding", "Padding", 4, 2, 8, 1).unit("px")
            .description("Space between a widget's edge and its text. Smaller widgets take less of the screen."));

    // ------------------------------------------------------------------ visibility (blocks)

    public final NumberSetting playerDistance = add(distance("playerDistance", "Player render distance",
            "Players farther away are not drawn."));
    public final NumberSetting entityDistance = add(distance("entityDistance", "Entity render distance",
            "Mobs, animals, minecarts and other entities (not players or dropped items) farther away are not drawn."));
    public final NumberSetting itemDistance = add(distance("itemDistance", "Item render distance",
            "Dropped items farther away are not drawn."));
    public final NumberSetting blockEntityDistance = add(distance("blockEntityDistance", "Block entity render distance",
            "Chests, signs, banners, skulls and other block entities farther away are not drawn (beacon beams always are)."));
    public final NumberSetting particleDistance = add(distance("particleDistance", "Particle distance",
            "Particles that would appear farther away are not created."));
    public final NumberSetting nameTagDistance = add(distance("nameTagDistance", "Name tag distance",
            "Name tags above players and named mobs farther away are hidden."));
    public final NumberSetting waypointDistance = add(distance("waypointDistance", "Waypoint distance",
            "Waypoints farther away disappear from the screen and the world."));
    public final NumberSetting cosmeticDistance = add(distance("cosmeticDistance", "Cosmetic render distance",
            "Capes, hats and wings of players farther away are not drawn."));

    // ------------------------------------------------------------------ rendering

    public final ModeSetting<FogMode> fog = add(new ModeSetting<FogMode>("fog", "Distance fog", FogMode.VANILLA)
            .description("The fog that hides the edge of the render distance. Fog under water, in lava and from effects is never changed."));
    public final BooleanSetting animatedTextures = add(new BooleanSetting("animatedTextures", "Animated textures", true)
            .description("Water, lava, fire, portals and other animated blocks. Off freezes them and saves a little CPU and GPU time."));

    private boolean glass() {
        return hudLook.get() == HudLook.GLASS;
    }

    private static NumberSetting distance(String id, String name, String description) {
        return new NumberSetting(id, name, NO_LIMIT, 8, NO_LIMIT, 8).unit(" m")
                .maxLabel("setting.noLimit", "No limit").description(description);
    }

    private <S extends Setting<?>> S add(S setting) {
        setting.attach(this);
        settings.add(setting);
        return setting;
    }

    public void setChangeListener(Runnable listener) {
        this.changeListener = listener;
    }

    @Override
    public String settingsNamespace() {
        return "profile";
    }

    @Override
    public List<Setting<?>> settings() {
        return settings;
    }

    @Override
    public void onSettingChanged(Setting<?> setting) {
        if (changeListener != null) {
            changeListener.run();
        }
    }
}
