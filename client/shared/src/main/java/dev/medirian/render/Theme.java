package dev.medirian.render;

import java.util.Calendar;

/**
 * Colour tokens of the Medirian design system. Must stay in sync with
 * {@code launcher/src/renderer/styles/tokens.css}.
 *
 * <p>Seasonal themes only change tokens (and enable small decorative details such as the
 * Halloween fog/moon); components never hard-code colours.
 */
public final class Theme {

    /** User choice in Settings → General → Theme. */
    public enum Mode { AUTO, DEFAULT, HALLOWEEN, CHRISTMAS }

    public static final Theme DEFAULT = new Theme("default", false,
            0xB0080610, 0xF4100D17, 0xFF16131F, 0xFF1D1928, 0xFF2A2438, 0xFF3A3150,
            0xFFECEAF2, 0xFF9A94AB, 0xFF6C6680,
            0xFF9B55D6, 0xFFAE6FE3, 0x2E9B55D6, 0xFF9B55D6,
            0xFF5BC98A, 0xFFE5B454, 0xFFE5566A, 0x8C0B0A10);

    public static final Theme HALLOWEEN = new Theme("halloween", true,
            0xB80A0712, 0xF4120E1A, 0xFF181221, 0xFF1F182B, 0xFF2D243C, 0xFF3D3254,
            0xFFECEAF2, 0xFF9C95AE, 0xFF6E6683,
            0xFF9B55D6, 0xFFAE6FE3, 0x2E9B55D6, 0xFFE8833A,
            0xFF5BC98A, 0xFFE5B454, 0xFFE5566A, 0x8C0A0812);

    /** Winter night: cooler panels, the brand violet stays, icy blue as the seasonal accent, snowfall. */
    public static final Theme CHRISTMAS = new Theme("christmas", true,
            0xB8070A14, 0xF40E1020, 0xFF131726, 0xFF1A1F31, 0xFF262C42, 0xFF353D58,
            0xFFECEFF6, 0xFF97A0B6, 0xFF687089,
            0xFF9B55D6, 0xFFAE6FE3, 0x2E9B55D6, 0xFF8FD3F4,
            0xFF5BC98A, 0xFFE5B454, 0xFFE5566A, 0x8C090B14);

    private static volatile Theme current = resolve(Mode.AUTO);

    public final String id;
    /** Enables seasonal decorations (fog and moon, or snow). */
    public final boolean decorations;
    public final int backdrop;
    public final int panel;
    public final int surface;
    public final int elevated;
    public final int border;
    public final int borderStrong;
    public final int text;
    public final int textDim;
    public final int textMuted;
    public final int accent;
    public final int accentHover;
    public final int accentSoft;
    /** Seasonal accent (orange for Halloween); used sparingly. */
    public final int seasonal;
    public final int success;
    public final int warning;
    public final int danger;
    public final int hudBackground;

    private Theme(String id, boolean decorations, int backdrop, int panel, int surface, int elevated, int border,
                  int borderStrong, int text, int textDim, int textMuted, int accent, int accentHover, int accentSoft,
                  int seasonal, int success, int warning, int danger, int hudBackground) {
        this.id = id;
        this.decorations = decorations;
        this.backdrop = backdrop;
        this.panel = panel;
        this.surface = surface;
        this.elevated = elevated;
        this.border = border;
        this.borderStrong = borderStrong;
        this.text = text;
        this.textDim = textDim;
        this.textMuted = textMuted;
        this.accent = accent;
        this.accentHover = accentHover;
        this.accentSoft = accentSoft;
        this.seasonal = seasonal;
        this.success = success;
        this.warning = warning;
        this.danger = danger;
        this.hudBackground = hudBackground;
    }

    public static Theme current() {
        return current;
    }

    public static void apply(Mode mode) {
        current = resolve(mode);
    }

    /**
     * AUTO picks the seasonal theme: Halloween during October, Christmas from December to
     * 6 January, otherwise the default theme.
     */
    public static Theme resolve(Mode mode) {
        switch (mode) {
            case HALLOWEEN:
                return HALLOWEEN;
            case CHRISTMAS:
                return CHRISTMAS;
            case DEFAULT:
                return DEFAULT;
            default:
                return seasonal(Calendar.getInstance());
        }
    }

    static Theme seasonal(Calendar date) {
        int month = date.get(Calendar.MONTH);
        if (month == Calendar.OCTOBER) {
            return HALLOWEEN;
        }
        if (month == Calendar.DECEMBER || (month == Calendar.JANUARY && date.get(Calendar.DAY_OF_MONTH) <= 6)) {
            return CHRISTMAS;
        }
        return DEFAULT;
    }

    /** Snow instead of fog in the seasonal decorations. */
    public boolean snow() {
        return this == CHRISTMAS;
    }
}
