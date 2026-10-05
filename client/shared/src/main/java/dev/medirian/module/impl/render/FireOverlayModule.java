package dev.medirian.module.impl.render;

import dev.medirian.module.Category;
import dev.medirian.module.Module;
import dev.medirian.platform.Capability;
import dev.medirian.setting.NumberSetting;

/** The flames shown on screen while burning: lower and more transparent so they hide less. */
public final class FireOverlayModule extends Module {

    private final NumberSetting height;
    private final NumberSetting opacity;

    public FireOverlayModule() {
        super("fireoverlay", "Fire Overlay", Category.RENDER, "Lower and fade the flames on screen while you burn.");
        requires(Capability.FIRE_OVERLAY);
        height = add(new NumberSetting("height", "Height", 40, 0, 100, 5).unit("%")
                .description("100% is vanilla."));
        opacity = add(new NumberSetting("opacity", "Opacity", 70, 10, 100, 5).unit("%")
                .description("100% is vanilla."));
    }

    /** Added to the overlay's vertical position (0 = vanilla; the overlay is one unit tall). */
    public float offsetY() {
        return isEnabled() ? -(1f - height.floatValue() / 100f) * 0.5f : 0f;
    }

    public float alpha(float vanilla) {
        return isEnabled() ? vanilla * opacity.floatValue() / 100f : vanilla;
    }
}
