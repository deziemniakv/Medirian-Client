package dev.meridian.module.impl.render;

import dev.meridian.module.Category;
import dev.meridian.module.Module;
import dev.meridian.platform.Capability;
import dev.meridian.setting.NumberSetting;

/** Raises the lightmap gamma so dark areas are visible (render only, no gameplay change). */
public final class FullbrightModule extends Module {

    private final NumberSetting level;

    public FullbrightModule() {
        super("fullbright", "Fullbright", Category.RENDER, "See in the dark without night vision.");
        requires(Capability.FULLBRIGHT);
        level = add(new NumberSetting("level", "Brightness", 1000, 100, 1500, 50).unit("%"));
    }

    public double gamma(double vanilla) {
        return isEnabled() ? level.doubleValue() / 100.0 : vanilla;
    }
}
