package dev.meridian.input;

import dev.meridian.config.GlobalSettings;
import dev.meridian.module.Module;
import dev.meridian.module.ModuleManager;
import dev.meridian.setting.KeySetting;
import dev.meridian.setting.Setting;

import java.util.ArrayList;
import java.util.List;

/**
 * Dispatches key and mouse button events (received only while playing, never while typing in a
 * screen) to module keybinds, extra {@link KeySetting}s and global shortcuts.
 */
public final class KeybindManager {

    /** Global shortcuts handled by the core (open menus). */
    public interface GlobalActions {
        void openModMenu();

        void openHudEditor();

        /** Plays the equipped emote. */
        void playEmote();
    }

    private final ModuleManager modules;
    private final GlobalSettings global;
    private final GlobalActions actions;
    private final List<KeySetting> held = new ArrayList<KeySetting>();

    public KeybindManager(ModuleManager modules, GlobalSettings global, GlobalActions actions) {
        this.modules = modules;
        this.global = global;
        this.actions = actions;
    }

    public void onKey(Key key, boolean pressed) {
        if (key == Key.NONE) {
            return;
        }
        if (pressed) {
            if (key == global.modMenuKey.key()) {
                actions.openModMenu();
                return;
            }
            if (key == global.hudEditorKey.key()) {
                actions.openHudEditor();
                return;
            }
            if (key == global.emoteKey.key()) {
                actions.playEmote();
                return;
            }
        }
        for (Module module : modules.all()) {
            KeySetting bind = module.keybind();
            if (bind.key() == key && (module.isEnabled() || module.keybindWorksWhenDisabled())) {
                fire(bind, pressed);
            }
            if (!module.isEnabled()) {
                continue;
            }
            List<Setting<?>> settings = module.settings();
            for (int i = 0; i < settings.size(); i++) {
                Setting<?> setting = settings.get(i);
                if (setting instanceof KeySetting && ((KeySetting) setting).key() == key) {
                    fire((KeySetting) setting, pressed);
                }
            }
        }
    }

    private void fire(KeySetting bind, boolean pressed) {
        if (pressed) {
            if (!bind.isDown()) {
                bind.press();
                held.add(bind);
            }
        } else {
            bind.release();
            held.remove(bind);
        }
    }

    /** Releases every held binding, e.g. when a screen opens while a hold-key is pressed. */
    public void releaseAll() {
        for (KeySetting bind : new ArrayList<KeySetting>(held)) {
            bind.release();
        }
        held.clear();
    }
}
