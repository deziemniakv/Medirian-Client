package dev.medirian.config;

import dev.medirian.perf.PerformanceProfile;
import dev.medirian.setting.ModeSetting;
import dev.medirian.setting.NumberSetting;
import dev.medirian.setting.Setting;
import dev.medirian.setting.SettingsOwner;

import java.util.ArrayList;
import java.util.List;

/** Visual/performance settings stored inside each configuration profile. */
public final class ProfileSettings implements SettingsOwner {

    private final List<Setting<?>> settings = new ArrayList<Setting<?>>();
    private Runnable changeListener;

    public final NumberSetting hudScale = add(new NumberSetting("hudScale", "HUD scale", 1.0, 0.5, 2.0, 0.05).unit("x"));
    public final ModeSetting<PerformanceProfile> performanceProfile = add(new ModeSetting<PerformanceProfile>(
            "performanceProfile", "Performance mode", PerformanceProfile.BALANCED)
            .description("Applies real optimisations: particle limits, entity render distance, background FPS cap and UI animations."));

    private <S extends Setting<?>> S add(S setting) {
        setting.attach(this);
        settings.add(setting);
        return setting;
    }

    public void setChangeListener(Runnable listener) {
        this.changeListener = listener;
    }

    @Override
    public String settingsNamespace() {
        return "profile";
    }

    @Override
    public List<Setting<?>> settings() {
        return settings;
    }

    @Override
    public void onSettingChanged(Setting<?> setting) {
        if (changeListener != null) {
            changeListener.run();
        }
    }
}
