package dev.meridian.platform;

/** Read-only view of the game state. Implementations read live game data; calls must be cheap. */
public interface GameView {

    boolean inWorld();

    boolean isSingleplayer();

    /** Address of the connected server, or null in singleplayer / menus. */
    String serverAddress();

    /** Number of players in the tab list, or -1 when unknown. */
    int onlinePlayers();

    /** Latency to the current server in ms, or -1 when unknown (singleplayer). */
    int ping();

    /** The game's own FPS counter (updated once per second by Minecraft). */
    int fps();

    /** The local player, or null when not in a world. */
    PlayerView player();

    /** Localised name of the biome at the player position, or null. */
    String biome();

    /** Current sidebar objective, or null when the server shows none. */
    SidebarView sidebar();

    /** HUD hidden with F1. */
    boolean hudHidden();

    /** Debug overlay (F3) visible. */
    boolean debugOverlay();

    /** Any non-Meridian screen is open (chat, inventory, pause menu…). */
    boolean screenOpen();

    boolean windowFocused();

    /** Camera is in a third person perspective. */
    boolean thirdPerson();
}
