package dev.medirian.render;

import java.util.Calendar;

/**
 * Colour tokens of Medirian Client: a cozy Halloween night. The logo's purple is the main colour,
 * pumpkin orange the accent, and everything sits on deep violet-black. Must stay in sync with
 * {@code launcher/src/renderer/src/styles/tokens.css} and {@code scripts/pixel/palette.mjs}.
 *
 * <p>Halloween is Medirian's identity, not a season: the palette never changes. The only seasonal
 * touch is snow over the night scenes in winter (December to 6 January), which can be switched off.
 */
public final class Theme {

    private static final Theme CURRENT = new Theme();
    private static volatile boolean winterSnow = true;
    private static volatile boolean snowPreview;

    /** Dims the game behind Medirian's screens. */
    public final int backdrop = 0xC40A0612;
    /** Window fill and its bevel (light top-left, dark bottom-right). */
    public final int panel = 0xFF1B1228;
    public final int panelLight = 0xFF2E2042;
    public final int panelDark = 0xFF0E0916;
    /** Raised elements inside windows: tiles, buttons, key caps. */
    public final int surface = 0xFF261A37;
    public final int surfaceLight = 0xFF3A2954;
    public final int surfaceDark = 0xFF150E1F;
    public final int elevated = 0xFF31234A;
    /** Sunken wells: text fields, tracks, lists. */
    public final int inset = 0xFF0F0A18;
    public final int border = 0xFF3A2A52;
    public final int borderStrong = 0xFF4E3A6C;
    /** The ink outline every pixel element gets. */
    public final int outline = 0xFF07040C;
    public final int text = 0xFFF3EADB;
    public final int textDim = 0xFFB9AACB;
    public final int textMuted = 0xFF7C6D92;
    /** Brand purple. */
    public final int accent = 0xFF9B5FD0;
    public final int accentHover = 0xFFC49AE8;
    public final int accentDark = 0xFF5C1D7C;
    public final int accentSoft = 0x339B5FD0;
    /** Pumpkin orange: enabled states, primary actions, highlights. */
    public final int pumpkin = 0xFFE07A2F;
    public final int pumpkinLight = 0xFFF39C4A;
    public final int pumpkinDark = 0xFFA8481A;
    public final int ember = 0xFFFFBF66;
    /** Dark text on pumpkin. */
    public final int onPumpkin = 0xFF2A1206;
    public final int success = 0xFF7CC36A;
    public final int warning = 0xFFF2C14E;
    public final int danger = 0xFFD9534F;
    public final int hudBackground = 0x9C0C0814;

    private Theme() {
    }

    public static Theme current() {
        return CURRENT;
    }

    /** Settings → General → Snow in winter. */
    public static void setWinterSnow(boolean on) {
        winterSnow = on;
    }

    /** Shows the winter snow whatever the date (the self-test's screenshot of it). */
    public static void previewSnow(boolean on) {
        snowPreview = on;
    }

    /** Whether snow falls over the night scenes right now. */
    public static boolean snowing() {
        return snowPreview || (winterSnow && isWinter(Calendar.getInstance()));
    }

    /** December to 6 January. */
    static boolean isWinter(Calendar date) {
        int month = date.get(Calendar.MONTH);
        return month == Calendar.DECEMBER || (month == Calendar.JANUARY && date.get(Calendar.DAY_OF_MONTH) <= 6);
    }
}
