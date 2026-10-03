package dev.meridian.config;

import com.google.gson.JsonObject;
import dev.meridian.core.Log;

/**
 * Upgrades profile JSON written by older Meridian versions to {@link ConfigManager#FORMAT_VERSION}.
 * Add a step here whenever the profile format changes; never break old files.
 */
final class ConfigMigrations {

    private ConfigMigrations() {
    }

    static JsonObject migrate(JsonObject json) {
        int version = json.has("version") && json.get("version").isJsonPrimitive() ? json.get("version").getAsInt() : 1;
        if (version > ConfigManager.FORMAT_VERSION) {
            Log.warn("Profile was written by a newer Meridian (format {}); unknown fields are ignored", version);
        }
        // Format 1 is the first public format; future migrations go here, e.g.
        // if (version < 2) { rename settings...; version = 2; }
        return json;
    }
}
