package dev.medirian.module.impl.render;

import dev.medirian.module.Category;
import dev.medirian.module.Module;
import dev.medirian.platform.Capability;
import dev.medirian.setting.NumberSetting;

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
