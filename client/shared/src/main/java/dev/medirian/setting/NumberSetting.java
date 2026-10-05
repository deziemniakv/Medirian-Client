package dev.medirian.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.Locale;

/** A numeric value constrained to {@code [min, max]} and snapped to {@code step}. */
public final class NumberSetting extends Setting<Double> {

    private final double min;
    private final double max;
    private final double step;
    private String unit = "";

    public NumberSetting(String id, String name, double defaultValue, double min, double max, double step) {
        super(id, name, defaultValue);
        this.min = min;
        this.max = max;
        this.step = step;
        this.value = sanitize(defaultValue);
    }

    public NumberSetting unit(String unit) {
        this.unit = unit;
        return this;
    }

    public double min() {
        return min;
    }

    public double max() {
        return max;
    }

    public double step() {
        return step;
    }

    public int intValue() {
        return (int) Math.round(value);
    }

    public float floatValue() {
        return value.floatValue();
    }

    public double doubleValue() {
        return value;
    }

    public void set(double v) {
        set(Double.valueOf(v));
    }

    /** Position of the current value within the range, 0..1. */
    public double progress() {
        return max == min ? 0 : (value - min) / (max - min);
    }

    public void setProgress(double progress) {
        set(min + (max - min) * Math.max(0, Math.min(1, progress)));
    }

    public String formatted() {
        int decimals = step >= 1 ? 0 : step >= 0.1 ? 1 : 2;
        String number = decimals == 0
                ? String.valueOf(Math.round(value))
                : String.format(Locale.ROOT, "%." + decimals + "f", value);
        return unit.isEmpty() ? number : number + unit;
    }

    @Override
    protected Double sanitize(Double candidate) {
        if (candidate == null || candidate.isNaN() || candidate.isInfinite()) {
            return defaultValue();
        }
        double v = Math.max(min, Math.min(max, candidate));
        if (step > 0) {
            v = min + Math.round((v - min) / step) * step;
            v = Math.max(min, Math.min(max, v));
            // remove floating point noise such as 0.30000000000000004
            v = Math.round(v * 1_000_000d) / 1_000_000d;
        }
        return v;
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(value);
    }

    @Override
    public void fromJson(JsonElement json) {
        if (json != null && json.isJsonPrimitive() && json.getAsJsonPrimitive().isNumber()) {
            set(json.getAsDouble());
        }
    }
}
