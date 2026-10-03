package dev.meridian.render;

/**
 * Composite drawing helpers built on {@link Gfx} primitives.
 *
 * <p>Rounded corners are rasterised as horizontal spans at real-pixel resolution (the transform
 * is temporarily scaled by {@code 1 / pixelScale}), so corners are smooth-ish at every GUI scale
 * while costing only a handful of fills.
 */
public final class UiDraw {

    private UiDraw() {
    }

    /** Rounded rectangle; {@code radius} in GUI units. */
    public static void roundRect(Gfx g, float x, float y, float w, float h, float radius, int argb) {
        if (Colors.alpha(argb) == 0 || w <= 0 || h <= 0) {
            return;
        }
        double scale = g.pixelScale();
        int px = (int) Math.round(x * scale);
        int py = (int) Math.round(y * scale);
        int pw = (int) Math.round(w * scale);
        int ph = (int) Math.round(h * scale);
        int r = (int) Math.min(Math.round(radius * scale), Math.min(pw, ph) / 2);
        g.push();
        float inv = (float) (1.0 / scale);
        g.scale(inv, inv);
        if (r <= 0) {
            g.fill(px, py, px + pw, py + ph, argb);
        } else {
            g.fill(px, py + r, px + pw, py + ph - r, argb);
            for (int i = 0; i < r; i++) {
                // horizontal inset of this scanline of the corner arc
                double dy = r - i - 0.5;
                int inset = (int) Math.round(r - Math.sqrt(Math.max(0, r * r - dy * dy)));
                g.fill(px + inset, py + i, px + pw - inset, py + i + 1, argb);
                g.fill(px + inset, py + ph - i - 1, px + pw - inset, py + ph - i, argb);
            }
        }
        g.pop();
    }

    /** Rounded rectangle with a 1 real-pixel border. */
    public static void roundRectBordered(Gfx g, float x, float y, float w, float h, float radius, int fill, int border) {
        float px = (float) (1.0 / g.pixelScale());
        roundRect(g, x, y, w, h, radius, border);
        roundRect(g, x + px, y + px, w - 2 * px, h - 2 * px, Math.max(0, radius - px), fill);
    }

    /** 1 GUI-unit outline. */
    public static void outline(Gfx g, int x, int y, int w, int h, int argb) {
        g.fill(x, y, x + w, y + 1, argb);
        g.fill(x, y + h - 1, x + w, y + h, argb);
        g.fill(x, y + 1, x + 1, y + h - 1, argb);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, argb);
    }

    /** Thin (1 real pixel) outline. */
    public static void hairline(Gfx g, float x, float y, float w, float h, int argb) {
        double scale = g.pixelScale();
        int px = (int) Math.round(x * scale);
        int py = (int) Math.round(y * scale);
        int pw = (int) Math.round(w * scale);
        int ph = (int) Math.round(h * scale);
        g.push();
        float inv = (float) (1.0 / scale);
        g.scale(inv, inv);
        g.fill(px, py, px + pw, py + 1, argb);
        g.fill(px, py + ph - 1, px + pw, py + ph, argb);
        g.fill(px, py + 1, px + 1, py + ph - 1, argb);
        g.fill(px + pw - 1, py + 1, px + pw, py + ph - 1, argb);
        g.pop();
    }

    /** Soft drop shadow made of a few translucent rounded layers. */
    public static void shadow(Gfx g, float x, float y, float w, float h, float radius, int layers) {
        for (int i = layers; i >= 1; i--) {
            roundRect(g, x - i, y - i + 1, w + 2 * i, h + 2 * i, radius + i, Colors.argb(18, 0, 0, 0));
        }
    }

    public static void centeredText(Gfx g, String text, float centerX, float y, int argb, boolean shadow) {
        g.text(text, centerX - g.textWidth(text) / 2f, y, argb, shadow);
    }

    /** Trims {@code text} with an ellipsis so it fits in {@code maxWidth}. */
    public static String ellipsize(Gfx g, String text, int maxWidth) {
        if (g.textWidth(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "...";
        int available = maxWidth - g.textWidth(ellipsis);
        int end = text.length();
        while (end > 0 && g.textWidth(text.substring(0, end)) > available) {
            end--;
        }
        return text.substring(0, end) + ellipsis;
    }

    /** Greedy word wrap into lines no wider than {@code maxWidth}. */
    public static java.util.List<String> wrap(Gfx g, String text, int maxWidth) {
        java.util.List<String> lines = new java.util.ArrayList<String>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String candidate = line.length() == 0 ? word : line + " " + word;
            if (g.textWidth(candidate) <= maxWidth || line.length() == 0) {
                line.setLength(0);
                line.append(candidate);
            } else {
                lines.add(line.toString());
                line.setLength(0);
                line.append(word);
            }
        }
        if (line.length() > 0) {
            lines.add(line.toString());
        }
        return lines;
    }

    /** Draws a crescent moon (Halloween decoration). */
    public static void moon(Gfx g, float cx, float cy, float radius, int argb, int background) {
        circle(g, cx, cy, radius, argb);
        circle(g, cx + radius * 0.45f, cy - radius * 0.25f, radius * 0.85f, background);
    }

    /** Filled circle rasterised at real-pixel resolution. */
    public static void circle(Gfx g, float cx, float cy, float radius, int argb) {
        double scale = g.pixelScale();
        int pcx = (int) Math.round(cx * scale);
        int pcy = (int) Math.round(cy * scale);
        int pr = (int) Math.max(1, Math.round(radius * scale));
        g.push();
        float inv = (float) (1.0 / scale);
        g.scale(inv, inv);
        for (int dy = -pr; dy < pr; dy++) {
            double yy = dy + 0.5;
            int half = (int) Math.round(Math.sqrt(Math.max(0, pr * pr - yy * yy)));
            if (half > 0) {
                g.fill(pcx - half, pcy + dy, pcx + half, pcy + dy + 1, argb);
            }
        }
        g.pop();
    }
}
