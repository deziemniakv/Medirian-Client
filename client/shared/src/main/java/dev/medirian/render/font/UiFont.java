package dev.medirian.render.font;

import dev.medirian.i18n.I18n;
import dev.medirian.setting.ModeSetting;

/** The font of Medirian's menus and HUD. */
public enum UiFont implements ModeSetting.Labeled {
    MINECRAFT,
    MEDIRIAN;

    @Override
    public String label() {
        return this == MINECRAFT ? "Minecraft" : I18n.tr("font.medirian", "Medirian (smooth)");
    }
}
