package dev.medirian.util;

import java.util.Locale;

/** Formatting helpers for HUD text. */
public final class Format {

    private static final String[] ROMAN = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};

    private Format() {
    }

    /** {@code 83_000 → "1:23"}, {@code 4_983_000 → "1:23:03"}. */
    public static String duration(long ms) {
        long totalSeconds = Math.max(0, ms / 1000);
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        if (hours > 0) {
            return hours + ":" + two(minutes) + ":" + two(seconds);
        }
        return minutes + ":" + two(seconds);
    }

    /** Duration with tenths of a second, e.g. {@code "1:23.4"}. */
    public static String preciseDuration(long ms) {
        return duration(ms) + "." + (Math.max(0, ms) % 1000) / 100;
    }

    /** Minecraft ticks (20/s) as {@code m:ss}. */
    public static String ticks(int ticks) {
        return duration(ticks * 50L);
    }

    public static String bytes(long bytes) {
        double mb = bytes / (1024.0 * 1024.0);
        if (mb >= 1024) {
            return String.format(Locale.ROOT, "%.1f GB", mb / 1024.0);
        }
        return Math.round(mb) + " MB";
    }

    public static String roman(int level) {
        return level >= 0 && level < ROMAN.length ? ROMAN[level] : String.valueOf(level);
    }

    public static String decimals(double value, int decimals) {
        if (decimals <= 0) {
            return String.valueOf(Math.round(value));
        }
        return String.format(Locale.ROOT, "%." + decimals + "f", value);
    }

    private static String two(long value) {
        return value < 10 ? "0" + value : String.valueOf(value);
    }
}
