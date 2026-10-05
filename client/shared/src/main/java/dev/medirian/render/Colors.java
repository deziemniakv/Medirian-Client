package dev.medirian.render;

/** ARGB colour helpers. */
public final class Colors {

    private Colors() {
    }

    public static int alpha(int argb) {
        return argb >>> 24;
    }

    public static int red(int argb) {
        return (argb >> 16) & 0xFF;
    }

    public static int green(int argb) {
        return (argb >> 8) & 0xFF;
    }

    public static int blue(int argb) {
        return argb & 0xFF;
    }

    public static int argb(int a, int r, int g, int b) {
        return (clamp(a) << 24) | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
    }

    /** Replaces the alpha channel. */
    public static int withAlpha(int argb, int alpha) {
        return (clamp(alpha) << 24) | (argb & 0x00FFFFFF);
    }

    /** Multiplies the alpha channel by {@code factor} (0..1). */
    public static int fade(int argb, float factor) {
        return withAlpha(argb, Math.round(alpha(argb) * Math.max(0f, Math.min(1f, factor))));
    }

    /** Linear interpolation between two colours, {@code t} in 0..1. */
    public static int lerp(int from, int to, float t) {
        if (t <= 0) {
            return from;
        }
        if (t >= 1) {
            return to;
        }
        return argb(
                Math.round(alpha(from) + (alpha(to) - alpha(from)) * t),
                Math.round(red(from) + (red(to) - red(from)) * t),
                Math.round(green(from) + (green(to) - green(from)) * t),
                Math.round(blue(from) + (blue(to) - blue(from)) * t));
    }

    /** HSV → ARGB (h, s, v in 0..1). */
    public static int hsv(float h, float s, float v, int alpha) {
        float hue = (h - (float) Math.floor(h)) * 6f;
        int sector = (int) hue;
        float f = hue - sector;
        float p = v * (1 - s);
        float q = v * (1 - f * s);
        float t = v * (1 - (1 - f) * s);
        float r;
        float g;
        float b;
        switch (sector) {
            case 0: r = v; g = t; b = p; break;
            case 1: r = q; g = v; b = p; break;
            case 2: r = p; g = v; b = t; break;
            case 3: r = p; g = q; b = v; break;
            case 4: r = t; g = p; b = v; break;
            default: r = v; g = p; b = q; break;
        }
        return argb(alpha, Math.round(r * 255), Math.round(g * 255), Math.round(b * 255));
    }

    /** ARGB → {h, s, v} in 0..1. */
    public static float[] toHsv(int argb) {
        float r = red(argb) / 255f;
        float g = green(argb) / 255f;
        float b = blue(argb) / 255f;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float delta = max - min;
        float h;
        if (delta == 0) {
            h = 0;
        } else if (max == r) {
            h = ((g - b) / delta) / 6f;
        } else if (max == g) {
            h = ((b - r) / delta + 2) / 6f;
        } else {
            h = ((r - g) / delta + 4) / 6f;
        }
        if (h < 0) {
            h += 1;
        }
        float s = max == 0 ? 0 : delta / max;
        return new float[] {h, s, max};
    }

    private static int clamp(int channel) {
        return channel < 0 ? 0 : (channel > 255 ? 255 : channel);
    }
}
