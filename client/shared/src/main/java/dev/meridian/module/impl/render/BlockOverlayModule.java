package dev.meridian.module.impl.render;

import dev.meridian.module.Category;
import dev.meridian.module.Module;
import dev.meridian.platform.Capability;
import dev.meridian.platform.Hooks;
import dev.meridian.setting.BooleanSetting;
import dev.meridian.setting.ColorSetting;
import dev.meridian.setting.NumberSetting;

/** Custom colour and thickness for the outline of the block you are looking at, with optional fill. */
public final class BlockOverlayModule extends Module {

    private final ColorSetting color;
    private final NumberSetting thickness;
    private final BooleanSetting fill;
    private final ColorSetting fillColor;

    public BlockOverlayModule() {
        super("blockoverlay", "Block Overlay", Category.RENDER, "Recolor and thicken the selected block outline, optionally filled.");
        requires(Capability.BLOCK_OUTLINE);
        color = add(new ColorSetting("color", "Outline color", 0xE69B55D6));
        thickness = add(new NumberSetting("thickness", "Thickness", 1.5, 0.5, 5, 0.25).unit("x")
                .description("Relative to the vanilla outline width.")
                .visibleWhen(() -> Hooks.supports(Capability.BLOCK_OUTLINE_WIDTH)));
        fill = add(new BooleanSetting("fill", "Fill", false));
        fillColor = add(new ColorSetting("fillColor", "Fill color", 0x2E9B55D6).visibleWhen(fill::on));
    }

    public int outlineColor(int vanilla) {
        return isEnabled() ? color.argb() : vanilla;
    }

    public float outlineWidth(float vanilla) {
        return isEnabled() ? vanilla * thickness.floatValue() : vanilla;
    }

    /** ARGB fill colour, or 0 when filling is off. */
    public int fillColor() {
        return isEnabled() && fill.on() ? fillColor.argb() : 0;
    }
}
