package dev.meridian.mc1_8_9;

import dev.meridian.account.PlayerIdentity;
import dev.meridian.input.Key;
import dev.meridian.platform.Capability;
import dev.meridian.platform.ClientActions;
import dev.meridian.platform.GameView;
import dev.meridian.platform.InputView;
import dev.meridian.platform.Platform;
import dev.meridian.platform.PlayerView;
import dev.meridian.platform.SidebarView;
import dev.meridian.ui.MeridianScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.SettingsScreen;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.Session;
import net.minecraft.entity.player.ClientPlayerEntity;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.ScoreboardPlayerScore;
import net.minecraft.scoreboard.Team;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;
import org.lwjgl.Sys;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;

import java.awt.Desktop;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** {@link Platform} implementation for Minecraft 1.8.9. */
public final class LegacyPlatform implements Platform, GameView, InputView, ClientActions {

    public static final String TARGET_ID = "1.8.9";

    private static LegacyPlatform instance;

    private final MinecraftClient client;
    private final LegacyGfx gfx;
    // 1.8.9 draws status effects only in the inventory, so there is nothing to hide on the HUD
    private final Set<Capability> capabilities = EnumSet.complementOf(EnumSet.of(Capability.HIDE_VANILLA_EFFECTS));
    private final String[] keyLabels = new String[GameKey.values().length];

    private Views.LocalPlayerView playerView;
    private int ticks;
    private String biome;
    private int ping = -1;
    private int onlinePlayers = -1;
    private Views.Sidebar sidebar;
    private boolean optionsDirty;

    private LegacyPlatform(MinecraftClient client) {
        this.client = client;
        this.gfx = new LegacyGfx(client);
    }

    public static LegacyPlatform create(MinecraftClient client) {
        instance = new LegacyPlatform(client);
        return instance;
    }

    public static LegacyPlatform get() {
        return instance;
    }

    public LegacyGfx gfx() {
        return gfx;
    }

    /** Refreshes cached values; called every client tick before Meridian's tick. */
    public void tick() {
        ticks++;
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null) {
            playerView = null;
            biome = null;
            sidebar = null;
            ping = -1;
            onlinePlayers = -1;
        } else {
            if (playerView == null || playerView.entity != player) {
                playerView = new Views.LocalPlayerView(player);
            }
            if (ticks % 10 == 0) {
                Biome b = client.world.getBiome(new BlockPos(player));
                biome = b == null ? null : b.name;
            }
            if (ticks % 5 == 0) {
                sidebar = readSidebar(player);
            }
            if (ticks % 20 == 0) {
                readConnection(player);
            }
        }
        if (ticks % 40 == 1) {
            for (GameKey key : GameKey.values()) {
                keyLabels[key.ordinal()] = shortLabel(binding(key).getCode());
            }
        }
        if (optionsDirty && ticks % 20 == 0) {
            optionsDirty = false;
            client.options.save();
        }
    }

    private void readConnection(ClientPlayerEntity player) {
        ClientPlayNetworkHandler handler = client.getNetworkHandler();
        if (handler == null) {
            ping = -1;
            onlinePlayers = -1;
            return;
        }
        onlinePlayers = handler.getPlayerList().size();
        PlayerListEntry entry = handler.getPlayerListEntry(player.getUuid());
        ping = entry == null || client.isIntegratedServerRunning() ? -1 : entry.getLatency();
    }

    private Views.Sidebar readSidebar(ClientPlayerEntity player) {
        Scoreboard scoreboard = client.world.getScoreboard();
        ScoreboardObjective objective = null;
        Team team = scoreboard.getPlayerTeam(player.getGameProfile().getName());
        if (team != null) {
            int color = team.getFormatting().getColorIndex();
            if (color >= 0) {
                objective = scoreboard.getObjectiveForSlot(3 + color);
            }
        }
        if (objective == null) {
            objective = scoreboard.getObjectiveForSlot(1);
        }
        if (objective == null) {
            return null;
        }
        List<ScoreboardPlayerScore> scores = new ArrayList<ScoreboardPlayerScore>();
        for (ScoreboardPlayerScore score : scoreboard.getAllPlayerScores(objective)) {
            if (score.getPlayerName() != null && !score.getPlayerName().startsWith("#")) {
                scores.add(score);
            }
        }
        // vanilla order is ascending; the sidebar shows the 15 highest, highest first
        if (scores.size() > 15) {
            scores = scores.subList(scores.size() - 15, scores.size());
        }
        List<String> lines = new ArrayList<String>();
        List<String> values = new ArrayList<String>();
        for (int i = scores.size() - 1; i >= 0; i--) {
            ScoreboardPlayerScore score = scores.get(i);
            lines.add(Team.decorateName(scoreboard.getPlayerTeam(score.getPlayerName()), score.getPlayerName()));
            values.add(Formatting.RED.toString() + score.getScore());
        }
        return new Views.Sidebar(objective.getDisplayName(), lines, values);
    }

    private static String shortLabel(int code) {
        if (code < 0) {
            int button = code + 100;
            switch (button) {
                case 0: return "LMB";
                case 1: return "RMB";
                case 2: return "MMB";
                default: return "M" + (button + 1);
            }
        }
        String name = Keyboard.getKeyName(code);
        if (name == null) {
            return "?";
        }
        return name.length() <= 1 ? name.toUpperCase() : name.charAt(0) + name.substring(1).toLowerCase();
    }

    private KeyBinding binding(GameKey key) {
        GameOptions options = client.options;
        switch (key) {
            case FORWARD: return options.forwardKey;
            case BACK: return options.backKey;
            case LEFT: return options.leftKey;
            case RIGHT: return options.rightKey;
            case JUMP: return options.jumpKey;
            case SNEAK: return options.sneakKey;
            case SPRINT: return options.sprintKey;
            case ATTACK: return options.attackKey;
            default: return options.useKey;
        }
    }

    // ------------------------------------------------------------------ Platform

    @Override
    public String minecraftVersion() {
        return "1.8.9";
    }

    @Override
    public String targetId() {
        return TARGET_ID;
    }

    @Override
    public boolean supports(Capability capability) {
        return capabilities.contains(capability);
    }

    @Override
    public GameView game() {
        return this;
    }

    @Override
    public InputView input() {
        return this;
    }

    @Override
    public ClientActions actions() {
        return this;
    }

    @Override
    public PlayerIdentity identity() {
        Session session = client.getSession();
        String token = session.getAccessToken();
        int dots = 0;
        if (token != null) {
            for (int i = 0; i < token.length(); i++) {
                if (token.charAt(i) == '.') {
                    dots++;
                }
            }
        }
        // Microsoft-authenticated sessions carry a JWT access token; offline/dev sessions do not.
        return new PlayerIdentity(session.getUsername(), session.getUuid(), dots == 2);
    }

    @Override
    public void openScreen(MeridianScreen screen) {
        client.setScreen(screen == null ? null : new ScreenBridge(screen));
    }

    @Override
    public MeridianScreen currentScreen() {
        return client.currentScreen instanceof ScreenBridge ? ((ScreenBridge) client.currentScreen).meridian() : null;
    }

    // ------------------------------------------------------------------ GameView

    @Override
    public boolean inWorld() {
        return client.world != null && client.player != null;
    }

    @Override
    public boolean isSingleplayer() {
        return client.isIntegratedServerRunning();
    }

    @Override
    public String serverAddress() {
        ServerInfo server = client.getCurrentServerEntry();
        return server == null || client.isIntegratedServerRunning() ? null : server.address;
    }

    @Override
    public int onlinePlayers() {
        return onlinePlayers;
    }

    @Override
    public int ping() {
        return ping;
    }

    @Override
    public int fps() {
        return MinecraftClient.getCurrentFps();
    }

    @Override
    public PlayerView player() {
        return client.player == null ? null : playerView;
    }

    @Override
    public String biome() {
        return biome;
    }

    @Override
    public SidebarView sidebar() {
        return sidebar;
    }

    @Override
    public boolean hudHidden() {
        return client.options.hudHidden;
    }

    @Override
    public boolean debugOverlay() {
        return client.options.debugEnabled;
    }

    @Override
    public boolean screenOpen() {
        Screen screen = client.currentScreen;
        return screen != null && !(screen instanceof ScreenBridge);
    }

    @Override
    public boolean windowFocused() {
        return Display.isActive();
    }

    @Override
    public boolean thirdPerson() {
        return client.options.perspective != 0;
    }

    // ------------------------------------------------------------------ InputView

    @Override
    public boolean isDown(GameKey key) {
        int code = binding(key).getCode();
        if (code < 0) {
            return Mouse.isButtonDown(code + 100);
        }
        return code > 0 && code < Keyboard.KEYBOARD_SIZE && Keyboard.isKeyDown(code);
    }

    @Override
    public String label(GameKey key) {
        String label = keyLabels[key.ordinal()];
        return label == null ? "?" : label;
    }

    @Override
    public boolean isKeyDown(Key key) {
        if (key.isMouse()) {
            return Mouse.isButtonDown(key.mouseButton());
        }
        int code = KeyCodes.toLwjgl(key);
        return code > 0 && Keyboard.isKeyDown(code);
    }

    // ------------------------------------------------------------------ ClientActions

    @Override
    public void openVanillaSettings() {
        client.setScreen(new SettingsScreen(client.currentScreen, client.options));
    }

    @Override
    public boolean isFullscreen() {
        return client.isFullscreen();
    }

    @Override
    public void toggleFullscreen() {
        client.toggleFullscreen();
        optionsDirty = true;
    }

    @Override
    public int maxFps() {
        return client.options.maxFramerate;
    }

    @Override
    public void setMaxFps(int fps) {
        client.options.maxFramerate = fps;
        optionsDirty = true;
    }

    @Override
    public int unlimitedFps() {
        return 260;
    }

    @Override
    public List<String> applyVideoPreset(VideoPreset preset) {
        GameOptions options = client.options;
        List<String> changes = new ArrayList<String>();
        int clouds = preset == VideoPreset.PERFORMANCE ? 0 : preset == VideoPreset.BALANCED ? 1 : 2;
        if (options.cloudMode != clouds) {
            options.cloudMode = clouds;
            changes.add("Clouds");
        }
        int particles = preset == VideoPreset.PERFORMANCE ? 2 : preset == VideoPreset.BALANCED ? 1 : 0;
        if (options.particle != particles) {
            options.particle = particles;
            changes.add("Particles");
        }
        boolean shadows = preset != VideoPreset.PERFORMANCE;
        if (options.entityShadows != shadows) {
            options.entityShadows = shadows;
            changes.add("Entity shadows");
        }
        int maxDistance = preset == VideoPreset.PERFORMANCE ? 8 : preset == VideoPreset.BALANCED ? 12 : 16;
        if (options.viewDistance > maxDistance) {
            options.viewDistance = maxDistance;
            changes.add("Render distance " + maxDistance);
        }
        if (!changes.isEmpty()) {
            options.save();
        }
        return changes.isEmpty() ? Collections.<String>emptyList() : changes;
    }

    @Override
    public int perspective() {
        return client.options.perspective;
    }

    @Override
    public void setPerspective(int perspective) {
        client.options.perspective = Math.max(0, Math.min(2, perspective));
    }

    @Override
    public void sendChat(String message) {
        if (client.player != null) {
            client.player.sendChatMessage(message);
        }
    }

    @Override
    public File gameDirectory() {
        return client.runDirectory;
    }

    @Override
    public File screenshotsDirectory() {
        return new File(client.runDirectory, "screenshots");
    }

    @Override
    public void openFolder(File folder) {
        folder.mkdirs();
        try {
            Desktop.getDesktop().open(folder);
        } catch (Throwable t) {
            Sys.openURL("file://" + folder.getAbsolutePath());
        }
    }

    @Override
    public void setClipboard(String text) {
        Screen.setClipboard(text);
    }
}
