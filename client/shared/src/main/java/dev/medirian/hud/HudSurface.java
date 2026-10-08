package dev.medirian.hud;

import dev.medirian.config.ProfileSettings;
import dev.medirian.render.Colors;
import dev.medirian.render.Gfx;
import dev.medirian.render.UiDraw;

/**
 * The surface every HUD widget sits on, in the style of Settings → HUD:
 *
 * <ul>
 *   <li><b>Liquid Glass</b> — the blurred world behind the widget ({@link Gfx#backdrop}), a deep
 *       violet tint whose opacity is the "Glass opacity" setting, a faint light edge ("Border") and
 *       a soft highlight along the top, with rounded corners. Subtle on purpose: no gloss, no glow.</li>
 *   <li><b>Classic</b> — the widget's own background colour, flat.</li>
 * </ul>
 *
 * The blur is prepared once per frame by {@link HudManager}; drawing a surface only samples it.
 */
public final class HudSurface {

    /** The glass tint: Medirian's night violet. */
    private static final int TINT = 0x1A1129;
    /** The light of the edge and highlight: moonlit lilac. */
    private static final int LIGHT = 0xE6D6FF;

    private static ProfileSettings settings;

    private HudSurface() {
    }

    static void bind(ProfileSettings profileSettings) {
        settings = profileSettings;
    }

    public static boolean glass() {
        return settings != null && settings.hudLook.get() == ProfileSettings.HudLook.GLASS;
    }

    /** Corner radius of widgets, in GUI units. */
    public static float radius() {
        return settings == null ? 2.5f : settings.hudRadius.floatValue();
    }

    /** Space between a widget's edge and its content, in GUI units. */
    public static int padding() {
        return settings == null ? 4 : settings.hudPadding.intValue();
    }

    /** Blur passes for this frame (0 = no blur). */
    static int blurStrength() {
        if (!glass()) {
            return 0;
        }
        int blur = settings.glassBlur.intValue();
        return blur <= 0 ? 0 : Math.max(1, Math.min(4, (int) Math.ceil(blur / 25.0)));
    }

    /**
     * Draws a widget surface at ({@code x}, {@code y}) of {@code w}×{@code h}.
     *
     * @param classic the colour used by the Classic style (the widget's background colour)
     */
    public static void panel(Gfx g, float x, float y, float w, float h, int classic) {
        float r = Math.min(radius(), Math.min(w, h) / 2f);
        if (!glass()) {
            if (r > 0) {
                UiDraw.roundRect(g, x, y, w, h, r, classic);
            } else {
                g.fill(Math.round(x), Math.round(y), Math.round(x + w), Math.round(y + h), classic);
            }
            return;
        }
        boolean blurred = settings.glassBlur.intValue() > 0 && g.backdropReady();
        if (blurred) {
            UiDraw.roundBackdrop(g, x, y, w, h, r);
        }
        // without blur the tint alone carries the glass, so it is a little denser
        float opacity = settings.glassOpacity.floatValue() / 100f;
        int alpha = Math.round(255 * Math.min(0.96f, blurred ? opacity * 0.82f : opacity));
        UiDraw.roundRect(g, x, y, w, h, r, (alpha << 24) | TINT);
        float border = settings.glassBorder.floatValue() / 100f;
        if (border > 0) {
            UiDraw.roundOutline(g, x, y, w, h, r, Colors.argb(Math.round(110 * border), (LIGHT >> 16) & 0xFF, (LIGHT >> 8) & 0xFF, LIGHT & 0xFF));
            // a soft highlight just inside the top edge, like light on the rim of a glass
            float px = (float) (1.0 / g.pixelScale());
            if (w > 2 * r + 2) {
                highlight(g, x + r, y + px, w - 2 * r, Colors.argb(Math.round(60 * border), 255, 255, 255));
            }
        }
    }

    /** A one real-pixel line of {@code argb}, fading out towards both ends. */
    private static void highlight(Gfx g, float x, float y, float w, int argb) {
        double scale = g.pixelScale();
        int px = (int) Math.round(x * scale);
        int py = (int) Math.round(y * scale);
        int pw = (int) Math.round(w * scale);
        g.push();
        float inv = (float) (1.0 / scale);
        g.scale(inv, inv);
        int fade = Math.max(1, pw / 4);
        int steps = Math.min(fade, 6);
        for (int i = 0; i < steps; i++) {
            float t = (i + 1f) / (steps + 1f);
            int segment = fade / steps;
            int color = Colors.fade(argb, t);
            g.fill(px + i * segment, py, px + (i + 1) * segment, py + 1, color);
            g.fill(px + pw - (i + 1) * segment, py, px + pw - i * segment, py + 1, color);
        }
        g.fill(px + steps * (fade / steps), py, px + pw - steps * (fade / steps), py + 1, argb);
        g.pop();
    }
}
