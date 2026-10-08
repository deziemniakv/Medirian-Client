package dev.medirian.platform;

import dev.medirian.account.PlayerIdentity;
import dev.medirian.ui.MedirianScreen;

/**
 * The contract every Minecraft version adapter implements. This is the only way shared code
 * talks to the game.
 */
public interface Platform {

    /** Minecraft version, e.g. {@code "1.8.9"}. */
    String minecraftVersion();

    /** Medirian target id (matches the release manifest), e.g. {@code "1.21.11"}. */
    String targetId();

    boolean supports(Capability capability);

    GameView game();

    InputView input();

    ClientActions actions();

    /** The Minecraft account the game was started with. */
    PlayerIdentity identity();

    /**
     * The game session's access token. Only ever sent to Mojang's session server (signing in to
     * Medirian services), never to Medirian's own servers.
     */
    String accessToken();

    /** Opens a Medirian screen, or returns to the game when {@code screen} is null. */
    void openScreen(MedirianScreen screen);

    /** The Medirian screen currently shown, or null. */
    MedirianScreen currentScreen();

    /** Minecraft's own options for Settings → Advanced, or null when the adapter has none. */
    default VanillaOptions vanillaOptions() {
        return null;
    }
}
