package dev.meridian.module.impl.combat;

import dev.meridian.module.Category;
import dev.meridian.module.Module;
import dev.meridian.platform.Capability;
import dev.meridian.setting.ColorSetting;
import dev.meridian.setting.NumberSetting;

/**
 * Colour of the flash an entity shows when it takes damage. Intensity is relative to vanilla
 * (100% = vanilla strength) because 1.8.9 and modern versions blend the overlay differently.
 */
public final class HitColorModule extends Module {

    private final ColorSetting color;
    private final NumberSetting intensity;

    public HitColorModule() {
        super("hitcolor", "Hit Color", Category.COMBAT, "Change the color entities flash when they take damage.");
        requires(Capability.HIT_COLOR);
        color = add(new ColorSetting("color", "Color", 0xFF9B55D6, false));
        intensity = add(new NumberSetting("intensity", "Intensity", 100, 10, 200, 5).unit("%"));
    }

    /** Opaque RGB colour, or 0 when the module is off (vanilla red). */
    public int color() {
        return isEnabled() ? color.argb() | 0xFF000000 : 0;
    }

    public float intensity() {
        return isEnabled() ? intensity.floatValue() / 100f : 1f;
    }
}
