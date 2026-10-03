package dev.meridian.setting;

import java.util.List;

/** Something that owns a list of settings: a module or a global settings page. */
public interface SettingsOwner {

    /** Namespace used for translation keys and persistence, e.g. a module id. */
    String settingsNamespace();

    List<Setting<?>> settings();

    /** Called after any owned setting changed; used to schedule a config save. */
    void onSettingChanged(Setting<?> setting);
}
