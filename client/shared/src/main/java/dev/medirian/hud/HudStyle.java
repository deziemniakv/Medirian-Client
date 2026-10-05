package dev.medirian.hud;

import dev.medirian.module.Module;
import dev.medirian.render.Colors;
import dev.medirian.render.Gfx;
import dev.medirian.render.UiDraw;
import dev.medirian.setting.BooleanSetting;
import dev.medirian.setting.ColorSetting;

/** Standard appearance settings shared by text-based HUD elements. */
public final class HudStyle {

    public static final int DEFAULT_TEXT = 0xFFF2EFF7;
    public static final int DEFAULT_LABEL = 0xFFB98AE8;
    public static final int DEFAULT_BACKGROUND = 0x8C0B0A10;
    public static final String GROUP = "Appearance";

    public final BooleanSetting background;
    public final ColorSetting backgroundColor;
    public final BooleanSetting rounded;
    public final ColorSetting textColor;
    public final ColorSetting labelColor;
    public final BooleanSetting shadow;

    public HudStyle(Module module, boolean withLabel) {
        background = module.add(new BooleanSetting("background", "Background", true).group(GROUP));
        backgroundColor = module.add(new ColorSetting("backgroundColor", "Background color", DEFAULT_BACKGROUND)
                .group(GROUP).visibleWhen(background::on));
        rounded = module.add(new BooleanSetting("rounded", "Rounded corners", true)
                .group(GROUP).visibleWhen(background::on));
        textColor = module.add(new ColorSetting("textColor", "Text color", DEFAULT_TEXT).group(GROUP));
        labelColor = withLabel
                ? module.add(new ColorSetting("labelColor", "Label color", DEFAULT_LABEL).group(GROUP))
                : null;
        shadow = module.add(new BooleanSetting("shadow", "Text shadow", true).group(GROUP));
    }

    /** Draws the element background box if enabled. */
    public void drawBackground(Gfx g, int width, int height) {
        if (!background.on() || Colors.alpha(backgroundColor.argb()) == 0) {
            return;
        }
        if (rounded.on()) {
            UiDraw.roundRect(g, 0, 0, width, height, 2.5f, backgroundColor.argb());
        } else {
            g.fill(0, 0, width, height, backgroundColor.argb());
        }
    }
}
