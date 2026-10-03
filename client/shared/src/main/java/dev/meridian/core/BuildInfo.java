package dev.meridian.core;

import java.io.InputStream;
import java.util.Properties;

/** Build metadata baked into the jar at build time ({@code meridian/build.properties}). */
public final class BuildInfo {

    public static final String VERSION;

    static {
        String version = "dev";
        InputStream in = BuildInfo.class.getResourceAsStream("/meridian/build.properties");
        if (in != null) {
            try {
                Properties properties = new Properties();
                properties.load(in);
                version = properties.getProperty("version", version);
            } catch (Exception e) {
                Log.warn("Could not read build.properties", e);
            } finally {
                try {
                    in.close();
                } catch (Exception ignored) {
                    // nothing to do
                }
            }
        }
        VERSION = version;
    }

    private BuildInfo() {
    }
}
