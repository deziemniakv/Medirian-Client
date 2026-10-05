package dev.medirian.module.impl.render;

import dev.medirian.module.Category;
import dev.medirian.module.Module;
import dev.medirian.platform.Capability;
import dev.medirian.setting.NumberSetting;

/** Camera shake when taking damage: 0% removes it, 100% is vanilla. The death animation is kept. */
public final class HurtCameraModule extends Module {

    private final NumberSetting strength;

    public HurtCameraModule() {
        super("hurtcam", "Hurt Camera", Category.RENDER, "Reduce or remove the camera shake when you take damage.");
        requires(Capability.HURT_CAMERA);
        strength = add(new NumberSetting("strength", "Strength", 0, 0, 100, 5).unit("%"));
    }

    /** Multiplier for the hurt tilt angle (1 = vanilla). */
    public float strength() {
        return isEnabled() ? strength.floatValue() / 100f : 1f;
    }
}
