package dev.medirian.module.impl.performance;

import dev.medirian.module.Category;
import dev.medirian.module.Module;
import dev.medirian.platform.Capability;
import dev.medirian.setting.NumberSetting;

/**
 * Caps the frame rate while the game window is not focused or minimised. Saves CPU/GPU (and
 * battery) when you alt-tab, without affecting gameplay FPS.
 */
public final class DynamicFpsModule extends Module {

    private final NumberSetting unfocusedFps;
    private final NumberSetting minimizedFps;

    public DynamicFpsModule() {
        super("dynamicfps", "Dynamic FPS", Category.PERFORMANCE, "Limits FPS while the game is in the background.");
        requires(Capability.DYNAMIC_FPS);
        enableByDefault();
        unfocusedFps = add(new NumberSetting("unfocusedFps", "Unfocused FPS", 30, 5, 120, 5));
        minimizedFps = add(new NumberSetting("minimizedFps", "Minimized FPS", 5, 1, 30, 1));
    }

    public int limit(int vanilla, boolean focused, boolean minimized) {
        if (!isEnabled() || focused) {
            return vanilla;
        }
        int cap = minimized ? minimizedFps.intValue() : unfocusedFps.intValue();
        return Math.min(vanilla, cap);
    }
}
