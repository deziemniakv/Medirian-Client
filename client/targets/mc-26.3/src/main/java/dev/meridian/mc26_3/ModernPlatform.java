package dev.meridian.mc26_3;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import dev.meridian.account.PlayerIdentity;
import dev.meridian.input.Key;
import dev.meridian.mc26_3.mixin.KeyMappingAccessor;
import dev.meridian.platform.CameraView;
import dev.meridian.platform.Capability;
import dev.meridian.platform.ClientActions;
import dev.meridian.platform.GameView;
import dev.meridian.platform.InputView;
import dev.meridian.platform.Platform;
import dev.meridian.platform.PlayerView;
import dev.meridian.platform.SidebarView;
import dev.meridian.platform.WorldKeys;
import dev.meridian.ui.MeridianScreen;
import net.minecraft.client.CameraType;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.User;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.network.chat.numbers.StyledFormat;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.util.Util;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** {@link Platform} implementation for Minecraft 26.3. */
public final class ModernPlatform implements Platform, GameView, InputView, ClientActions {

    public static final String TARGET_ID = "26.3";
    private static final Comparator<PlayerScoreEntry> SIDEBAR_ORDER = Comparator
            .comparing(PlayerScoreEntry::value).reversed()
            .thenComparing(PlayerScoreEntry::owner, String.CASE_INSENSITIVE_ORDER);

    private static ModernPlatform instance;

    private final Minecraft minecraft;
    /** Reused by {@link #isOccluder}: it runs thousands of times per frame. */
    private final BlockPos.MutableBlockPos occluderPos = new BlockPos.MutableBlockPos();
    private final Set<Capability> capabilities = EnumSet.allOf(Capability.class);
    private final ModernGfx gfx;
    private final ModernCamera camera;
    private final String[] keyLabels = new String[GameKey.values().length];

    private Views.LocalPlayerView playerView;
    private int ticks;
    private String biome;
    private int ping = -1;
    private int onlinePlayers = -1;
    private Views.Sidebar sidebar;
    private boolean optionsDirty;

    private ModernPlatform(Minecraft minecraft) {
        this.minecraft = minecraft;
        this.gfx = new ModernGfx(minecraft);
        this.camera = new ModernCamera(minecraft);
    }

    public static ModernPlatform create(Minecraft minecraft) {
        instance = new ModernPlatform(minecraft);
        return instance;
    }

    public static ModernPlatform get() {
        return instance;
    }

    public ModernGfx gfx() {
        return gfx;
    }

    /** Refreshes cached values; called every client tick before Meridian's tick. */
    public void tick() {
        ticks++;
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
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
                biome = readBiome(player);
            }
            if (ticks % 5 == 0) {
                sidebar = readSidebar(player);
            }
            if (ticks % 20 == 0) {
                readConnection(player);
            }
        }
        if (ticks % 40 == 1) {
            refreshKeyLabels();
        }
        HitColorTexture.update(minecraft);
        if (optionsDirty && ticks % 20 == 0) {
            optionsDirty = false;
            minecraft.options.save();
        }
    }

    private String readBiome(LocalPlayer player) {
        return minecraft.level.getBiome(player.blockPosition()).unwrapKey()
                .map(key -> Component.translatable("biome." + key.identifier().getNamespace() + "." + key.identifier().getPath()).getString())
                .orElse(null);
    }

    private void readConnection(LocalPlayer player) {
        ClientPacketListener connection = minecraft.getConnection();
        if (connection == null) {
            ping = -1;
            onlinePlayers = -1;
            return;
        }
        onlinePlayers = connection.getOnlinePlayers().size();
        PlayerInfo info = connection.getPlayerInfo(player.getUUID());
        ping = info == null || minecraft.isLocalServer() ? -1 : info.getLatency();
    }

    private Views.Sidebar readSidebar(LocalPlayer player) {
        Scoreboard scoreboard = minecraft.level.getScoreboard();
        Objective objective = null;
        PlayerTeam team = scoreboard.getPlayersTeam(player.getScoreboardName());
        if (team != null) {
            // the team-coloured sidebar slots are named after the team colour (TEAM_RED, ...)
            DisplaySlot slot = team.getColor().map(color -> DisplaySlot.valueOf("TEAM_" + color.name())).orElse(null);
            if (slot != null) {
                objective = scoreboard.getDisplayObjective(slot);
            }
        }
        if (objective == null) {
            objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
        }
        if (objective == null) {
            return null;
        }
        NumberFormat format = objective.numberFormatOrDefault(StyledFormat.SIDEBAR_DEFAULT);
        List<PlayerScoreEntry> entries = new ArrayList<>();
        for (PlayerScoreEntry entry : scoreboard.listPlayerScores(objective)) {
            if (!entry.isHidden()) {
                entries.add(entry);
            }
        }
        entries.sort(SIDEBAR_ORDER);
        List<Component> lines = new ArrayList<>();
        List<Component> scores = new ArrayList<>();
        for (int i = 0; i < Math.min(15, entries.size()); i++) {
            PlayerScoreEntry entry = entries.get(i);
            lines.add(PlayerTeam.formatNameForTeam(scoreboard.getPlayersTeam(entry.owner()), entry.ownerName()));
            Component score = entry.formatValue(format);
            scores.add(score.getString().isEmpty() ? null : score);
        }
        return new Views.Sidebar(objective.getDisplayName(), lines, scores);
    }

    private void refreshKeyLabels() {
        for (GameKey key : GameKey.values()) {
            keyLabels[key.ordinal()] = shortLabel(mapping(key));
        }
    }

    private static String shortLabel(KeyMapping mapping) {
        InputConstants.Key key = ((KeyMappingAccessor) mapping).meridian$getKey();
        if (key.getType() == InputConstants.Type.MOUSE) {
            switch (key.getValue()) {
                case 0: return "LMB";
                case 1: return "RMB";
                case 2: return "MMB";
                default: return "M" + (key.getValue() + 1);
            }
        }
        String name = mapping.getTranslatedKeyMessage().getString();
        return name.length() <= 1 ? name.toUpperCase() : name;
    }

    private KeyMapping mapping(GameKey key) {
        Options options = minecraft.options;
        switch (key) {
            case FORWARD: return options.keyUp;
            case BACK: return options.keyDown;
            case LEFT: return options.keyLeft;
            case RIGHT: return options.keyRight;
            case JUMP: return options.keyJump;
            case SNEAK: return options.keyShift;
            case SPRINT: return options.keySprint;
            case ATTACK: return options.keyAttack;
            default: return options.keyUse;
        }
    }

    // ------------------------------------------------------------------ Platform

    @Override
    public String minecraftVersion() {
        return "26.3";
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
    public String accessToken() {
        return minecraft.getUser().getAccessToken();
    }

    @Override
    public PlayerIdentity identity() {
        User user = minecraft.getUser();
        String token = user.getAccessToken();
        // Microsoft-authenticated sessions carry a JWT access token; offline/dev sessions do not.
        boolean online = token != null && token.chars().filter(c -> c == '.').count() == 2;
        return new PlayerIdentity(user.getName(), String.valueOf(user.getProfileId()), online);
    }

    @Override
    public void openScreen(MeridianScreen screen) {
        minecraft.gui.setScreen(screen == null ? null : new ScreenBridge(screen));
    }

    @Override
    public MeridianScreen currentScreen() {
        return minecraft.gui.screen() instanceof ScreenBridge bridge ? bridge.meridian() : null;
    }

    // ------------------------------------------------------------------ GameView

    @Override
    public boolean inWorld() {
        return minecraft.level != null && minecraft.player != null;
    }

    @Override
    public boolean isSingleplayer() {
        return minecraft.isLocalServer();
    }

    @Override
    public String serverAddress() {
        ServerData server = minecraft.getCurrentServer();
        return server == null || minecraft.isLocalServer() ? null : server.ip;
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
        return minecraft.getFps();
    }

    @Override
    public PlayerView player() {
        return minecraft.player == null ? null : playerView;
    }

    @Override
    public CameraView camera() {
        return minecraft.level == null ? null : camera;
    }

    @Override
    public String worldKey() {
        if (minecraft.level == null) {
            return null;
        }
        IntegratedServer server = minecraft.getSingleplayerServer();
        if (server != null) {
            Path root = server.getWorldPath(LevelResource.ROOT).normalize();
            return WorldKeys.local(String.valueOf(root.getFileName()));
        }
        ServerData data = minecraft.getCurrentServer();
        return WorldKeys.server(data == null ? null : data.ip);
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
        return minecraft.gui.hud.isHidden();
    }

    @Override
    public boolean debugOverlay() {
        return minecraft.getDebugOverlay().showDebugScreen();
    }

    @Override
    public boolean screenOpen() {
        return minecraft.gui.screen() != null && !(minecraft.gui.screen() instanceof ScreenBridge);
    }

    @Override
    public boolean windowFocused() {
        return minecraft.isWindowActive();
    }

    @Override
    public void forEachPlayer(PlayerVisitor visitor) {
        if (minecraft.level == null) {
            return;
        }
        for (net.minecraft.client.player.AbstractClientPlayer player : minecraft.level.players()) {
            visitor.visit(player.getUUID(), player == minecraft.player, player.getX(), player.getY(), player.getZ());
        }
    }

    @Override
    public void spawnParticle(dev.meridian.platform.TrailParticle particle, double x, double y, double z, double vx, double vy, double vz) {
        if (minecraft.level == null) {
            return;
        }
        net.minecraft.core.particles.SimpleParticleType type;
        switch (particle) {
            case HEART:
                type = net.minecraft.core.particles.ParticleTypes.HEART;
                break;
            case FLAME:
                type = net.minecraft.core.particles.ParticleTypes.FLAME;
                break;
            case SNOW:
                type = net.minecraft.core.particles.ParticleTypes.SNOWFLAKE;
                break;
            default:
                type = net.minecraft.core.particles.ParticleTypes.END_ROD;
                break;
        }
        minecraft.level.addParticle(type, x, y, z, vx, vy, vz);
    }

    @Override
    public boolean thirdPerson() {
        return !minecraft.options.getCameraType().isFirstPerson();
    }

    @Override
    public boolean isOccluder(int x, int y, int z) {
        Level level = minecraft.level;
        if (level == null) {
            return false;
        }
        BlockState state = level.getBlockState(occluderPos.set(x, y, z));
        return state.isSolidRender() && !(state.getBlock() instanceof LeavesBlock);
    }

    // ------------------------------------------------------------------ InputView

    @Override
    public boolean isDown(GameKey key) {
        InputConstants.Key bound = ((KeyMappingAccessor) mapping(key)).meridian$getKey();
        if (bound.getType() == InputConstants.Type.MOUSE) {
            return MouseButtons.isDown(bound.getValue());
        }
        return bound.getValue() >= 0 && InputConstants.isKeyDown(bound.getValue());
    }

    @Override
    public String label(GameKey key) {
        String label = keyLabels[key.ordinal()];
        return label == null ? "?" : label;
    }

    @Override
    public boolean isKeyDown(Key key) {
        if (key.isMouse()) {
            return MouseButtons.isDown(key.mouseButton());
        }
        int code = KeyCodes.toCode(key);
        return code >= 0 && InputConstants.isKeyDown(code);
    }

    // ------------------------------------------------------------------ ClientActions

    @Override
    public void openVanillaSettings() {
        minecraft.gui.setScreen(new OptionsScreen(minecraft.gui.screen(), minecraft.options));
    }

    @Override
    public boolean isFullscreen() {
        return minecraft.options.fullscreen().get();
    }

    @Override
    public void toggleFullscreen() {
        boolean fullscreen = !minecraft.options.fullscreen().get();
        minecraft.options.fullscreen().set(fullscreen);
        minecraft.getWindow().setFullscreen(fullscreen);
        optionsDirty = true;
    }

    @Override
    public int maxFps() {
        return minecraft.options.framerateLimit().get();
    }

    @Override
    public void setMaxFps(int fps) {
        minecraft.options.framerateLimit().set(fps);
        optionsDirty = true;
    }

    @Override
    public int unlimitedFps() {
        return 260;
    }

    @Override
    public List<String> applyVideoPreset(VideoPreset preset) {
        Options options = minecraft.options;
        List<String> changes = new ArrayList<>();
        CloudStatus clouds = preset == VideoPreset.PERFORMANCE ? CloudStatus.OFF : preset == VideoPreset.BALANCED ? CloudStatus.FAST : CloudStatus.FANCY;
        ParticleStatus particles = preset == VideoPreset.PERFORMANCE ? ParticleStatus.MINIMAL : preset == VideoPreset.BALANCED ? ParticleStatus.DECREASED : ParticleStatus.ALL;
        change(options.cloudStatus(), clouds, "Clouds", changes);
        change(options.particles(), particles, "Particles", changes);
        change(options.entityShadows(), preset != VideoPreset.PERFORMANCE, "Entity shadows", changes);
        change(options.biomeBlendRadius(), preset == VideoPreset.PERFORMANCE ? 0 : 2, "Biome blend", changes);
        int maxDistance = preset == VideoPreset.PERFORMANCE ? 8 : preset == VideoPreset.BALANCED ? 12 : 32;
        if (options.renderDistance().get() > maxDistance) {
            options.renderDistance().set(maxDistance);
            changes.add("Render distance " + maxDistance);
        }
        if (!changes.isEmpty()) {
            options.save();
        }
        return changes;
    }

    private static <T> void change(OptionInstance<T> option, T value, String label, List<String> changes) {
        if (!value.equals(option.get())) {
            option.set(value);
            changes.add(label);
        }
    }

    @Override
    public int perspective() {
        return minecraft.options.getCameraType().ordinal();
    }

    @Override
    public void setPerspective(int perspective) {
        CameraType[] types = CameraType.values();
        minecraft.options.setCameraType(types[Math.max(0, Math.min(types.length - 1, perspective))]);
    }

    @Override
    public void sendChat(String message) {
        ClientPacketListener connection = minecraft.getConnection();
        if (connection == null) {
            return;
        }
        if (message.startsWith("/")) {
            connection.sendCommand(message.substring(1));
        } else {
            connection.sendChat(message);
        }
    }

    @Override
    public File gameDirectory() {
        return minecraft.gameDirectory;
    }

    @Override
    public File screenshotsDirectory() {
        return new File(minecraft.gameDirectory, "screenshots");
    }

    @Override
    public void openFolder(File folder) {
        folder.mkdirs();
        com.mojang.blaze3d.Blaze3D.openPath(folder.toPath());
    }

    @Override
    public void setClipboard(String text) {
        minecraft.keyboardHandler.setClipboard(text);
    }

    @Override
    public void registerPluginChannels(List<String> channels) {
        send(MeridianPayload.REGISTER, String.join("\0", channels).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    @Override
    public void sendPluginMessage(String channel, byte[] data) {
        if (!MeridianPayload.HELLO.id().toString().equals(channel)) {
            throw new IllegalArgumentException("No payload type for plugin channel " + channel);
        }
        send(MeridianPayload.HELLO, data);
    }

    private void send(net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<MeridianPayload> type, byte[] data) {
        if (minecraft.getConnection() != null) {
            minecraft.getConnection().send(new net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket(new MeridianPayload(type, data)));
        }
    }

    @Override
    public String getClipboard() {
        String text = minecraft.keyboardHandler.getClipboard();
        return text == null ? "" : text;
    }
}
