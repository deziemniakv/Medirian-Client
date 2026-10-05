package dev.medirian.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/** An ARGB colour, persisted as {@code "#AARRGGBB"}. */
public final class ColorSetting extends Setting<Integer> {

    private final boolean alpha;

    public ColorSetting(String id, String name, int defaultArgb, boolean allowAlpha) {
        super(id, name, defaultArgb);
        this.alpha = allowAlpha;
    }

    public ColorSetting(String id, String name, int defaultArgb) {
        this(id, name, defaultArgb, true);
    }

    public int argb() {
        return value;
    }

    public boolean allowsAlpha() {
        return alpha;
    }

    @Override
    protected Integer sanitize(Integer candidate) {
        if (candidate == null) {
            return defaultValue();
        }
        return alpha ? candidate : (candidate | 0xFF000000);
    }

    public static String toHex(int argb) {
        return String.format("#%08X", argb);
    }

    /** Parses {@code #RRGGBB} or {@code #AARRGGBB}; returns null when invalid. */
    public static Integer parseHex(String text) {
        if (text == null) {
            return null;
        }
        String s = text.trim();
        if (s.startsWith("#")) {
            s = s.substring(1);
        }
        try {
            if (s.length() == 6) {
                return (int) (0xFF000000L | Long.parseLong(s, 16));
            }
            if (s.length() == 8) {
                return (int) Long.parseLong(s, 16);
            }
        } catch (NumberFormatException ignored) {
            // invalid
        }
        return null;
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(toHex(value));
    }

    @Override
    public void fromJson(JsonElement json) {
        if (json != null && json.isJsonPrimitive()) {
            Integer parsed = parseHex(json.getAsString());
            if (parsed != null) {
                set(parsed);
            }
        }
    }
}
