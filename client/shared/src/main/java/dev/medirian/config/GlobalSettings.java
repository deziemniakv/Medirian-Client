package dev.medirian.config;

import dev.medirian.i18n.I18n;
import dev.medirian.input.Key;
import dev.medirian.render.Theme;
import dev.medirian.setting.BooleanSetting;
import dev.medirian.setting.KeySetting;
import dev.medirian.setting.ModeSetting;
import dev.medirian.setting.Setting;
import dev.medirian.setting.SettingsOwner;

import java.util.ArrayList;
import java.util.List;

/**
 * Settings that apply to the whole client regardless of the active configuration profile.
 * Persisted in {@code config/client.json}.
 */
public final class GlobalSettings implements SettingsOwner {

    private final List<Setting<?>> settings = new ArrayList<Setting<?>>();
    private Runnable changeListener;

    public final ModeSetting<I18n.Language> language = add(new ModeSetting<I18n.Language>("language", "Language", I18n.Language.EN_US)
            .onChange(I18n::setLanguage));
    public final BooleanSetting mainMenu = add(new BooleanSetting("mainMenu", "Medirian main menu", true)
            .description("Medirian's own title screen instead of Minecraft's."));
    public final BooleanSetting winterSnow = add(new BooleanSetting("winterSnow", "Snow in winter", true)
            .description("Snow falls over the night from December to 6 January.")
            .onChange(Theme::setWinterSnow));
    public final ModeSetting<dev.medirian.render.font.UiFont> font = add(new ModeSetting<dev.medirian.render.font.UiFont>("font", "Font",
            dev.medirian.render.font.UiFont.MINECRAFT)
            .description("Medirian's smooth font stays sharp at every GUI scale."));
    public final BooleanSetting notifications = add(new BooleanSetting("notifications", "Notifications", true)
            .description("Small toasts for events like profile changes and screenshots."));
    public final BooleanSetting animations = add(new BooleanSetting("animations", "Animations", true)
            .description("Interface animations. Disable for the lightest possible UI."));
    public final BooleanSetting hideHudInDebug = add(new BooleanSetting("hideHudInDebug", "Hide HUD with F3", true));
    public final KeySetting modMenuKey = add(new KeySetting("modMenuKey", "Mod menu key", Key.RSHIFT));
    public final KeySetting hudEditorKey = add(new KeySetting("hudEditorKey", "HUD editor key", Key.NONE));
    public final KeySetting emoteKey = add(new KeySetting("emoteKey", "Emote key", Key.B));

    private <S extends Setting<?>> S add(S setting) {
        setting.attach(this);
        settings.add(setting);
        return setting;
    }

    /** Re-applies side effects of the current values (after loading from disk). */
    public void applyAll() {
        I18n.setLanguage(language.get());
        Theme.setWinterSnow(winterSnow.on());
    }

    public void setChangeListener(Runnable listener) {
        this.changeListener = listener;
    }

    @Override
    public String settingsNamespace() {
        return "global";
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
