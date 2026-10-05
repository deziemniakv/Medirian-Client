package dev.medirian.module.impl.render;

import dev.medirian.module.Category;
import dev.medirian.module.Module;
import dev.medirian.platform.Capability;
import dev.medirian.setting.ModeSetting;
import dev.medirian.setting.NumberSetting;

/** Client-side time of day for rendering (sky, light). The server time is not changed. */
public final class TimeChangerModule extends Module {

    /** Time presets in ticks. */
    public enum Preset {
        SUNRISE(23000), DAY(1000), NOON(6000), SUNSET(12000), NIGHT(14000), MIDNIGHT(18000), CUSTOM(-1);

        final long ticks;

        Preset(long ticks) {
            this.ticks = ticks;
        }
    }

    private final ModeSetting<Preset> preset;
    private final NumberSetting custom;

    public TimeChangerModule() {
        super("timechanger", "Time Changer", Category.RENDER, "Changes the rendered time of day.");
        requires(Capability.TIME_OVERRIDE);
        preset = add(new ModeSetting<Preset>("preset", "Time", Preset.NOON));
        custom = add(new NumberSetting("custom", "Custom time", 6000, 0, 23900, 100).unit(" ticks")
                .visibleWhen(() -> preset.is(Preset.CUSTOM)));
    }

    public long time(long vanilla) {
        if (!isEnabled()) {
            return vanilla;
        }
        long ticks = preset.is(Preset.CUSTOM) ? custom.intValue() : preset.get().ticks;
        // keep the day counter so the moon phase stays correct
        return (vanilla / 24000L) * 24000L + ticks;
    }
}
