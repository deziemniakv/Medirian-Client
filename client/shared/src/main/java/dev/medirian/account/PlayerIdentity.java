package dev.medirian.account;

/** The Minecraft account the game runs with (as reported by the game session). */
public final class PlayerIdentity {

    private final String name;
    private final String uuid;
    private final boolean online;

    /**
     * @param online true when the session has a real Minecraft access token (Microsoft account),
     *               false for offline/development sessions
     */
    public PlayerIdentity(String name, String uuid, boolean online) {
        this.name = name;
        this.uuid = uuid;
        this.online = online;
    }

    /**
     * The session's UUID with dashes. Development sessions may carry no real UUID (1.8.9 dev runs
     * pass the name); like the game, those players get the offline UUID derived from the name.
     */
    public static String sessionUuid(String raw, String name) {
        String hex = raw == null ? "" : raw.replace("-", "");
        if (hex.matches("[0-9a-fA-F]{32}")) {
            return (hex.substring(0, 8) + "-" + hex.substring(8, 12) + "-" + hex.substring(12, 16) + "-"
                    + hex.substring(16, 20) + "-" + hex.substring(20)).toLowerCase(java.util.Locale.ROOT);
        }
        return java.util.UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(java.nio.charset.Charset.forName("UTF-8"))).toString();
    }

    public String name() {
        return name;
    }

    public String uuid() {
        return uuid;
    }

    public boolean online() {
        return online;
    }
}
