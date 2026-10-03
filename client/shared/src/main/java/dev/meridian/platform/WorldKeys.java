package dev.meridian.platform;

import java.util.Locale;

/** Builds {@link GameView#worldKey()} values, so every adapter produces the same keys. */
public final class WorldKeys {

    private WorldKeys() {
    }

    /** {@code server:<host[:port]>}: lower case, without the default port or a trailing dot. */
    public static String server(String address) {
        String host = address == null ? "" : address.trim().toLowerCase(Locale.ROOT);
        if (host.endsWith(":25565")) {
            host = host.substring(0, host.length() - ":25565".length());
        }
        while (host.endsWith(".")) {
            host = host.substring(0, host.length() - 1);
        }
        return "server:" + (host.isEmpty() ? "unknown" : host);
    }

    /** {@code local:<save folder>} for singleplayer worlds. */
    public static String local(String saveFolder) {
        return "local:" + saveFolder;
    }
}
