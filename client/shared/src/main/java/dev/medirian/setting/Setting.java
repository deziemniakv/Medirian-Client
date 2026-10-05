package dev.medirian.setting;

import com.google.gson.JsonElement;
import dev.medirian.i18n.I18n;

import java.util.ArrayList;
import java.util.List;

/**
 * A typed, persisted, user-editable value.
 *
 * <p>Settings belong to a {@link SettingsOwner} (a module or a global settings page), which
 * provides the translation namespace: the label is looked up under
 * {@code setting.<owner>.<id>} with the English name as fallback.
 *
 * @param <T> value type
 */
public abstract class Setting<T> {

    /** Callback fired after the value changed. */
    public interface ChangeListener<T> {
        void changed(T value);
    }

    /** Condition deciding whether a setting is shown (e.g. only when a parent toggle is on). */
    public interface Visibility {
        boolean visible();
    }

    private final String id;
    private final String name;
    private T defaultValue;
    private final List<ChangeListener<T>> listeners = new ArrayList<ChangeListener<T>>(1);
    private SettingsOwner owner;
    private String group;
    private String description;
    private Visibility visibility;
    protected T value;

    protected Setting(String id, String name, T defaultValue) {
        this.id = id;
        this.name = name;
        this.defaultValue = defaultValue;
        this.value = defaultValue;
    }

    public final String id() {
        return id;
    }

    /**
     * Localised label: {@code setting.<owner>.<id>}, then the shared {@code setting.<id>} (common
     * settings such as "background" or "shadow"), then the English name.
     */
    public String displayName() {
        String ownerId = owner == null ? "global" : owner.settingsNamespace();
        return I18n.tr("setting." + ownerId + "." + id, I18n.tr("setting." + id, name));
    }

    /** Localised description, or null. */
    public String displayDescription() {
        if (description == null) {
            return null;
        }
        String ownerId = owner == null ? "global" : owner.settingsNamespace();
        return I18n.tr("setting." + ownerId + "." + id + ".desc", description);
    }

    public T get() {
        return value;
    }

    public T defaultValue() {
        return defaultValue;
    }

    public void set(T newValue) {
        T sanitized = sanitize(newValue);
        if (sanitized == null ? value == null : sanitized.equals(value)) {
            return;
        }
        value = sanitized;
        for (int i = 0; i < listeners.size(); i++) {
            listeners.get(i).changed(sanitized);
        }
        if (owner != null) {
            owner.onSettingChanged(this);
        }
    }

    /** Changes the default value (used for per-module default keybinds); also sets the value. */
    protected void redefineDefault(T newDefault) {
        this.defaultValue = newDefault;
        this.value = newDefault;
    }

    public void reset() {
        set(defaultValue);
    }

    public boolean isDefault() {
        return defaultValue == null ? value == null : defaultValue.equals(value);
    }

    /** Clamp/normalise a candidate value. Subclasses override for ranges and limits. */
    protected T sanitize(T candidate) {
        return candidate == null ? defaultValue : candidate;
    }

    public boolean isVisible() {
        return visibility == null || visibility.visible();
    }

    // ----- fluent configuration -----

    @SuppressWarnings("unchecked")
    public <S extends Setting<T>> S group(String group) {
        this.group = group;
        return (S) this;
    }

    @SuppressWarnings("unchecked")
    public <S extends Setting<T>> S description(String description) {
        this.description = description;
        return (S) this;
    }

    @SuppressWarnings("unchecked")
    public <S extends Setting<T>> S visibleWhen(Visibility visibility) {
        this.visibility = visibility;
        return (S) this;
    }

    @SuppressWarnings("unchecked")
    public <S extends Setting<T>> S onChange(ChangeListener<T> listener) {
        listeners.add(listener);
        return (S) this;
    }

    public String group() {
        return group;
    }

    public SettingsOwner owner() {
        return owner;
    }

    public void attach(SettingsOwner owner) {
        this.owner = owner;
    }

    // ----- persistence -----

    public abstract JsonElement toJson();

    /** Loads a persisted value. Invalid input must be ignored (keeping the current value). */
    public abstract void fromJson(JsonElement json);
}
