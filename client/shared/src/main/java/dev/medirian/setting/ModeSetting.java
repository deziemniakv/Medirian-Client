package dev.medirian.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import dev.medirian.i18n.I18n;

/** One option out of an enum. Persisted by constant name, so reordering constants is safe. */
public final class ModeSetting<E extends Enum<E>> extends Setting<E> {

    /** Enums with their own fixed labels (e.g. languages shown in their native name). */
    public interface Labeled {
        String label();
    }

    private final E[] options;

    public ModeSetting(String id, String name, E defaultValue) {
        super(id, name, defaultValue);
        this.options = defaultValue.getDeclaringClass().getEnumConstants();
    }

    public E[] options() {
        return options;
    }

    public boolean is(E option) {
        return value == option;
    }

    public void cycle(int direction) {
        int index = value.ordinal() + (direction >= 0 ? 1 : -1);
        if (index < 0) {
            index = options.length - 1;
        } else if (index >= options.length) {
            index = 0;
        }
        set(options[index]);
    }

    /** Localised label of an option: {@code mode.<EnumName>.<CONSTANT>}, falling back to "Title case". */
    public static String label(Enum<?> option) {
        if (option instanceof Labeled) {
            return ((Labeled) option).label();
        }
        String key = "mode." + option.getDeclaringClass().getSimpleName() + "." + option.name();
        return I18n.tr(key, prettify(option.name()));
    }

    public String valueLabel() {
        return label(value);
    }

    static String prettify(String constant) {
        String lower = constant.replace('_', ' ').toLowerCase();
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(value.name());
    }

    @Override
    public void fromJson(JsonElement json) {
        if (json == null || !json.isJsonPrimitive()) {
            return;
        }
        String name = json.getAsString();
        for (E option : options) {
            if (option.name().equals(name)) {
                set(option);
                return;
            }
        }
    }
}
