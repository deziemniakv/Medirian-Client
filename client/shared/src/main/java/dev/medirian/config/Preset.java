package dev.medirian.config;

import dev.medirian.module.Module;
import dev.medirian.module.ModuleManager;
import dev.medirian.perf.PerformanceProfile;

/**
 * Built-in configuration profiles generated on first run. Each preset starts from module
 * defaults ({@link Module#enabledByDefault()}) and applies its own differences.
 * Users can freely edit them; "Custom" profiles are any profiles created by the user.
 */
public enum Preset {
    DEFAULT("Default") {
        @Override
        void apply(ModuleManager modules, ProfileSettings settings) {
            // module defaults only
        }
    },
    PVP("PvP") {
        @Override
        void apply(ModuleManager modules, ProfileSettings settings) {
            enable(modules, "keystrokes", "cps", "reach", "combo", "targethud", "crosshair", "togglesprint", "ping");
            disable(modules, "coordinates");
        }
    },
    PERFORMANCE("Performance") {
        @Override
        void apply(ModuleManager modules, ProfileSettings settings) {
            settings.performanceProfile.set(PerformanceProfile.PERFORMANCE);
            enable(modules, "memory");
            disable(modules, "fpsgraph");
        }
    };

    private final String profileName;

    Preset(String profileName) {
        this.profileName = profileName;
    }

    public String profileName() {
        return profileName;
    }

    abstract void apply(ModuleManager modules, ProfileSettings settings);

    public static Preset byName(String name) {
        for (Preset preset : values()) {
            if (preset.profileName.equalsIgnoreCase(name)) {
                return preset;
            }
        }
        return null;
    }

    static void enable(ModuleManager modules, String... ids) {
        for (String id : ids) {
            Module module = modules.get(id);
            if (module != null) {
                module.setEnabled(true);
            }
        }
    }

    static void disable(ModuleManager modules, String... ids) {
        for (String id : ids) {
            Module module = modules.get(id);
            if (module != null) {
                module.setEnabled(false);
            }
        }
    }
}
