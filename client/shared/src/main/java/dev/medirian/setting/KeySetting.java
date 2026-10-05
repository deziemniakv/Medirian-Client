package dev.medirian.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import dev.medirian.input.Key;

/**
 * A key binding. Actions are dispatched by {@link dev.medirian.input.KeybindManager}, which
 * calls {@link #press()} / {@link #release()} for every registered key setting.
 */
public final class KeySetting extends Setting<Key> {

    /** Invoked when the bound key changes state. */
    public interface Action {
        void run();
    }

    private Action onPress;
    private Action onRelease;
    private boolean down;

    public KeySetting(String id, String name, Key defaultKey) {
        super(id, name, defaultKey);
    }

    /** Sets the default key (and current value); call only while constructing the owner. */
    public void setDefaultKey(Key key) {
        redefineDefault(key);
    }

    public Key key() {
        return value;
    }

    public boolean isBound() {
        return value != Key.NONE;
    }

    /** True while the bound key is physically held (tracked from key events). */
    public boolean isDown() {
        return down;
    }

    public KeySetting onPress(Action action) {
        this.onPress = action;
        return this;
    }

    public KeySetting onRelease(Action action) {
        this.onRelease = action;
        return this;
    }

    public void press() {
        down = true;
        if (onPress != null) {
            onPress.run();
        }
    }

    public void release() {
        if (!down) {
            return;
        }
        down = false;
        if (onRelease != null) {
            onRelease.run();
        }
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(value.name());
    }

    @Override
    public void fromJson(JsonElement json) {
        if (json != null && json.isJsonPrimitive()) {
            set(Key.byName(json.getAsString()));
        }
    }
}
