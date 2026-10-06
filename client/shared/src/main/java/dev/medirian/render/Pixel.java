package dev.medirian.render;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Medirian's pixel-art drawing kit. One pixel is one Medirian unit, which is always a whole number
 * of real pixels (see {@code UiScale}), so frames and icons stay crisp.
 *
 * <p>Every element has an ink outline with cut corners, like Minecraft's own windows and buttons;
 * raised elements are lit from the top left, sunken ones from the bottom right.
 */
public final class Pixel {

    private static final String ATLAS = "gui/icons.png";
    private static final Map<String, Integer> ICONS = new HashMap<String, Integer>();
    private static int atlasRows = 1;

    static {
        InputStream in = Pixel.class.getResourceAsStream("/assets/medirian/textures/gui/icons.txt");
        if (in != null) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                int index = 0;
                while ((line = reader.readLine()) != null) {
                    if (!line.trim().isEmpty()) {
                        ICONS.put(line.trim(), index++);
                    }
                }
                atlasRows = Math.max(1, (index + 15) / 16);
            } catch (java.io.IOException ignored) {
                // icons simply do not draw
            }
        }
    }

    private Pixel() {
    }

    /** A window: ink outline with cut corners, light top-left bevel, dark bottom-right bevel. */
    public static void panel(Gfx g, float x, float y, float w, float h) {
        Theme t = Theme.current();
        frame(g, x, y, w, h, t.panel, t.panelLight, t.panelDark);
    }

    /** Raised block in the given colours. */
    public static void frame(Gfx g, float fx, float fy, float fw, float fh, int fill, int light, int dark) {
        int x = Math.round(fx);
        int y = Math.round(fy);
        int w = Math.round(fw);
        int h = Math.round(fh);
        if (w < 3 || h < 3) {
            return;
        }
        outline(g, x, y, w, h, Theme.current().outline);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
        g.fill(x + 1, y + 1, x + w - 2, y + 2, light);
        g.fill(x + 1, y + 2, x + 2, y + h - 2, light);
        g.fill(x + 2, y + h - 2, x + w - 1, y + h - 1, dark);
        g.fill(x + w - 2, y + 2, x + w - 1, y + h - 2, dark);
    }

    /** Sunken well: dark top-left, light bottom-right. */
    public static void inset(Gfx g, float fx, float fy, float fw, float fh, int fill) {
        Theme t = Theme.current();
        int x = Math.round(fx);
        int y = Math.round(fy);
        int w = Math.round(fw);
        int h = Math.round(fh);
        if (w < 3 || h < 3) {
            return;
        }
        outline(g, x, y, w, h, t.outline);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, Colors.withAlpha(0x000000, 0x70));
        g.fill(x + 1, y + 2, x + 2, y + h - 1, Colors.withAlpha(0x000000, 0x50));
        g.fill(x + 2, y + h - 2, x + w - 1, y + h - 1, Colors.withAlpha(t.panelLight, 0x60));
    }

    /** One-pixel outline with the four corner pixels left out. */
    public static void outline(Gfx g, int x, int y, int w, int h, int argb) {
        g.fill(x + 1, y, x + w - 1, y + 1, argb);
        g.fill(x + 1, y + h - 1, x + w - 1, y + h, argb);
        g.fill(x, y + 1, x + 1, y + h - 1, argb);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, argb);
    }

    /** One-pixel ring just inside an element (hover and selection highlights). */
    public static void highlight(Gfx g, float fx, float fy, float fw, float fh, int argb) {
        int x = Math.round(fx);
        int y = Math.round(fy);
        int w = Math.round(fw);
        int h = Math.round(fh);
        outline(g, x - 1, y - 1, w + 2, h + 2, argb);
        g.fill(x, y, x + 1, y + 1, argb);
        g.fill(x + w - 1, y, x + w, y + 1, argb);
        g.fill(x, y + h - 1, x + 1, y + h, argb);
        g.fill(x + w - 1, y + h - 1, x + w, y + h, argb);
    }

    /** Horizontal separator with a light line under a dark one (an engraved groove). */
    public static void groove(Gfx g, float fx, float fy, float fw) {
        Theme t = Theme.current();
        int x = Math.round(fx);
        int y = Math.round(fy);
        int w = Math.round(fw);
        g.fill(x, y, x + w, y + 1, t.panelDark);
        g.fill(x, y + 1, x + w, y + 2, t.panelLight);
    }

    /** Vertical groove. */
    public static void grooveV(Gfx g, float fx, float fy, float fh) {
        Theme t = Theme.current();
        int x = Math.round(fx);
        int y = Math.round(fy);
        int h = Math.round(fh);
        g.fill(x, y, x + 1, y + h, t.panelDark);
        g.fill(x + 1, y, x + 2, y + h, t.panelLight);
    }

    // ------------------------------------------------------------------ icons

    /** Whether the icon atlas has {@code name}. */
    public static boolean hasIcon(String name) {
        return ICONS.containsKey(name);
    }

    /** Draws a 16×16 icon at {@code scale} (1 = 16 units), tinted (0xFFFFFFFF = as drawn). */
    public static void icon(Gfx g, String name, float x, float y, int scale, int tint) {
        Integer index = ICONS.get(name);
        if (index == null) {
            return;
        }
        float u0 = (index % 16) / 16f;
        float v0 = (index / 16) / (float) atlasRows;
        g.textureRegion(ATLAS, Math.round(x), Math.round(y), 16 * scale, 16 * scale, u0, v0, u0 + 1 / 16f, v0 + 1f / atlasRows, tint);
    }

    /** Icon name of a module (the atlas uses module ids). */
    public static String moduleIcon(String moduleId) {
        return hasIcon(moduleId) ? moduleId : "cat-misc";
    }

    /** Small "◆" made of pixels, used as a bullet in headers. */
    public static void diamond(Gfx g, float fx, float fy, int argb) {
        int x = Math.round(fx);
        int y = Math.round(fy);
        g.fill(x + 1, y, x + 2, y + 1, argb);
        g.fill(x, y + 1, x + 3, y + 2, argb);
        g.fill(x + 1, y + 2, x + 2, y + 3, argb);
    }

    /** Text with a 1-pixel ink drop shadow (crisper than the font shadow on dark panels). */
    public static void text(Gfx g, String text, float x, float y, int argb) {
        g.text(text, x + 1, y + 1, Theme.current().outline, false);
        g.text(text, x, y, argb, false);
    }
}
