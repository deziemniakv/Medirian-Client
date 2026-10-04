package dev.meridian.render.font;

import dev.meridian.i18n.I18n;
import dev.meridian.setting.ModeSetting;

/** The font of Meridian's menus and HUD. */
public enum UiFont implements ModeSetting.Labeled {
    MINECRAFT,
    MERIDIAN;

    @Override
    public String label() {
        return this == MINECRAFT ? "Minecraft" : I18n.tr("font.meridian", "Meridian (smooth)");
    }
}
