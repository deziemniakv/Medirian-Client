package dev.meridian.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/** Free text, limited to {@code maxLength} characters. */
public final class TextSetting extends Setting<String> {

    private final int maxLength;

    public TextSetting(String id, String name, String defaultValue, int maxLength) {
        super(id, name, defaultValue);
        this.maxLength = maxLength;
    }

    public int maxLength() {
        return maxLength;
    }

    @Override
    protected String sanitize(String candidate) {
        if (candidate == null) {
            return defaultValue();
        }
        return candidate.length() > maxLength ? candidate.substring(0, maxLength) : candidate;
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(value);
    }

    @Override
    public void fromJson(JsonElement json) {
        if (json != null && json.isJsonPrimitive()) {
            set(json.getAsString());
        }
    }
}
