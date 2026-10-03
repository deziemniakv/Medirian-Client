package dev.meridian.module.impl.render;

import dev.meridian.module.Category;
import dev.meridian.module.Module;
import dev.meridian.platform.Capability;
import dev.meridian.setting.ModeSetting;

/** Client-side rendered weather. The server weather is not changed. */
public final class WeatherChangerModule extends Module {

    /** Weather shown on the client. */
    public enum Weather { CLEAR, RAIN, THUNDER }

    private final ModeSetting<Weather> weather;

    public WeatherChangerModule() {
        super("weatherchanger", "Weather Changer", Category.RENDER, "Changes the rendered weather.");
        requires(Capability.WEATHER_OVERRIDE);
        weather = add(new ModeSetting<Weather>("weather", "Weather", Weather.CLEAR));
    }

    public float rain(float vanilla) {
        if (!isEnabled()) {
            return vanilla;
        }
        return weather.is(Weather.CLEAR) ? 0f : 1f;
    }

    public float thunder(float vanilla) {
        if (!isEnabled()) {
            return vanilla;
        }
        return weather.is(Weather.THUNDER) ? 1f : 0f;
    }
}
