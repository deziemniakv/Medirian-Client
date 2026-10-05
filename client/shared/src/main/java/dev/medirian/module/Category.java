package dev.medirian.module;

import dev.medirian.i18n.I18n;

public enum Category {
    COMBAT("Combat"),
    MOVEMENT("Movement"),
    PLAYER("Player"),
    RENDER("Render"),
    WORLD("World"),
    HUD("HUD"),
    MISC("Misc"),
    PERFORMANCE("Performance");

    private final String fallback;

    Category(String fallback) {
        this.fallback = fallback;
    }

    public String displayName() {
        return I18n.tr("category." + name().toLowerCase(), fallback);
    }
}
