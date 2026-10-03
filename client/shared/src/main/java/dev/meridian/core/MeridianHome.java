package dev.meridian.core;

import java.io.File;

/**
 * Resolves the Meridian data directory ({@code MERIDIAN_HOME}) shared by the launcher and the client.
 *
 * <p>Resolution order: {@code -Dmeridian.home} (set by the launcher), the {@code MERIDIAN_HOME}
 * environment variable, then the per-OS default. The defaults MUST match
 * {@code launcher/src/main/core/paths.ts}.
 */
public final class MeridianHome {

    private final File root;

    private MeridianHome(File root) {
        this.root = root;
    }

    public static MeridianHome resolve() {
        String property = System.getProperty("meridian.home");
        if (property != null && !property.trim().isEmpty()) {
            return new MeridianHome(new File(property.trim()));
        }
        String env = System.getenv("MERIDIAN_HOME");
        if (env != null && !env.trim().isEmpty()) {
            return new MeridianHome(new File(env.trim()));
        }
        return new MeridianHome(defaultRoot());
    }

    public static MeridianHome at(File root) {
        return new MeridianHome(root);
    }

    static File defaultRoot() {
        String os = System.getProperty("os.name", "").toLowerCase();
        String userHome = System.getProperty("user.home", ".");
        if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            return new File(appData != null ? appData : userHome, ".meridian");
        }
        if (os.contains("mac")) {
            return new File(userHome, "Library/Application Support/meridian");
        }
        return new File(userHome, ".meridian");
    }

    public File root() {
        return root;
    }

    /** {@code config/} — client settings and configuration profiles. */
    public File configDir() {
        return new File(root, "config");
    }

    /** {@code config/profiles/} — one JSON file per configuration profile. */
    public File profilesDir() {
        return new File(configDir(), "profiles");
    }

    @Override
    public String toString() {
        return root.getAbsolutePath();
    }
}
