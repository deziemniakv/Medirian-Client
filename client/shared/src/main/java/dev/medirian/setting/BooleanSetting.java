package dev.medirian.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public final class BooleanSetting extends Setting<Boolean> {

    public BooleanSetting(String id, String name, boolean defaultValue) {
        super(id, name, defaultValue);
    }

    public boolean on() {
        return value;
    }

    public void toggle() {
        set(!value);
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(value);
    }

    @Override
    public void fromJson(JsonElement json) {
        if (json != null && json.isJsonPrimitive() && json.getAsJsonPrimitive().isBoolean()) {
            set(json.getAsBoolean());
        }
    }
}
