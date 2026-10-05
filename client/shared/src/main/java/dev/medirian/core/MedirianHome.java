package dev.medirian.core;

import java.io.File;

/**
 * Resolves the Medirian data directory ({@code MEDIRIAN_HOME}) shared by the launcher and the client.
 *
 * <p>Resolution order: {@code -Dmedirian.home} (set by the launcher), the {@code MEDIRIAN_HOME}
 * environment variable, then the per-OS default. The defaults MUST match
 * {@code launcher/src/main/core/paths.ts}.
 */
public final class MedirianHome {

    private final File root;

    private MedirianHome(File root) {
        this.root = root;
    }

    public static MedirianHome resolve() {
        String property = System.getProperty("medirian.home");
        if (property != null && !property.trim().isEmpty()) {
            return new MedirianHome(new File(property.trim()));
        }
        String env = System.getenv("MEDIRIAN_HOME");
        if (env != null && !env.trim().isEmpty()) {
            return new MedirianHome(new File(env.trim()));
        }
        return new MedirianHome(defaultRoot());
    }

    public static MedirianHome at(File root) {
        return new MedirianHome(root);
    }

    static File defaultRoot() {
        String os = System.getProperty("os.name", "").toLowerCase();
        String userHome = System.getProperty("user.home", ".");
        if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            return new File(appData != null ? appData : userHome, ".medirian");
        }
        if (os.contains("mac")) {
            return new File(userHome, "Library/Application Support/medirian");
        }
        return new File(userHome, ".medirian");
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
