package dev.meridian.platform;

import dev.meridian.account.PlayerIdentity;
import dev.meridian.ui.MeridianScreen;

/**
 * The contract every Minecraft version adapter implements. This is the only way shared code
 * talks to the game.
 */
public interface Platform {

    /** Minecraft version, e.g. {@code "1.8.9"}. */
    String minecraftVersion();

    /** Meridian target id (matches the release manifest), e.g. {@code "1.21.11"}. */
    String targetId();

    boolean supports(Capability capability);

    GameView game();

    InputView input();

    ClientActions actions();

    /** The Minecraft account the game was started with. */
    PlayerIdentity identity();

    /** Opens a Meridian screen, or returns to the game when {@code screen} is null. */
    void openScreen(MeridianScreen screen);

    /** The Meridian screen currently shown, or null. */
    MeridianScreen currentScreen();
}
