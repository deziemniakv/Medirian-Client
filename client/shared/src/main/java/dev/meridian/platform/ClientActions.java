package dev.meridian.platform;

import java.io.File;
import java.util.List;

/** Actions Meridian can ask the game to perform. */
public interface ClientActions {

    /** Vanilla video presets applied by Settings → Performance. */
    enum VideoPreset { PERFORMANCE, BALANCED, QUALITY }

    /** Opens the vanilla options screen. */
    void openVanillaSettings();

    boolean isFullscreen();

    void toggleFullscreen();

    /** Vanilla max framerate option. {@link #unlimitedFps()} means unlimited. */
    int maxFps();

    void setMaxFps(int fps);

    int unlimitedFps();

    /**
     * Changes vanilla video options according to {@code preset} and saves them.
     *
     * @return human readable list of the options that were changed
     */
    List<String> applyVideoPreset(VideoPreset preset);

    /** Camera perspective: 0 = first person, 1 = third person back, 2 = third person front. */
    int perspective();

    void setPerspective(int perspective);

    /** Sends a chat message (or command when starting with '/') to the server. */
    void sendChat(String message);

    File gameDirectory();

    File screenshotsDirectory();

    /** Opens a folder in the system file manager. */
    void openFolder(File folder);

    void setClipboard(String text);

    /** Text on the system clipboard, or "" when there is none. */
    String getClipboard();

    /** Tells the server which plugin channels the client listens on (REGISTER / minecraft:register). */
    void registerPluginChannels(List<String> channels);

    /** Sends a plugin message to the server (play phase). Channels must be known to the adapter. */
    void sendPluginMessage(String channel, byte[] data);
}
