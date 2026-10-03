package dev.meridian.account;

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
