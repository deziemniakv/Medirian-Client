package dev.medirian.core;

import com.google.gson.JsonObject;
import dev.medirian.account.MedirianAccountService;
import dev.medirian.config.ConfigManager;
import dev.medirian.config.GlobalSettings;
import dev.medirian.config.ProfileSettings;
import dev.medirian.cosmetics.CosmeticsManager;
import dev.medirian.event.EventBus;
import dev.medirian.event.Events;
import dev.medirian.hud.HudManager;
import dev.medirian.i18n.I18n;
import dev.medirian.input.InputStats;
import dev.medirian.input.KeybindManager;
import dev.medirian.ipc.LauncherBridge;
import dev.medirian.module.BuiltinModules;
import dev.medirian.module.ModuleManager;
import dev.medirian.notify.NotificationManager;
import dev.medirian.perf.PerformanceManager;
import dev.medirian.platform.GameView;
import dev.medirian.platform.Hooks;
import dev.medirian.platform.Platform;
import dev.medirian.platform.PlayerView;
import dev.medirian.policy.ServerPolicy;
import dev.medirian.policy.ServerPolicyManager;
import dev.medirian.render.Gfx;
import dev.medirian.ui.HudEditorScreen;
import dev.medirian.ui.ModMenuScreen;
import dev.medirian.ui.SettingsScreen;
import dev.medirian.ui.UiScale;

import java.util.Arrays;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Entry point and service locator of Medirian. A version adapter calls {@link #boot(Platform)}
 * once the game client is initialised; after that the adapter only talks to {@link Hooks}.
 */
public final class Medirian {

    private static volatile Medirian instance;

    private final Platform platform;
    private final MedirianHome home;
    private final EventBus events = new EventBus();
    private final GlobalSettings global = new GlobalSettings();
    private final ProfileSettings profileSettings = new ProfileSettings();
    private final ModuleManager modules;
    private final HudManager hud;
    private final ConfigManager config;
    private final PerformanceManager performance;
    private final NotificationManager notifications = new NotificationManager();
    private final InputStats inputStats = new InputStats();
    private final KeybindManager keybinds;
    private final CosmeticsManager cosmetics;
    private final ServerPolicyManager policies;
    private final dev.medirian.services.MedirianServices services;
    private final dev.medirian.services.CloudProfiles cloudProfiles;
    private final dev.medirian.cosmetics.Trails trails = new dev.medirian.cosmetics.Trails();
    private final dev.medirian.cosmetics.emote.EmoteManager emotes;
    private dev.medirian.render.font.MedirianFont font;
    private boolean fontLoaded;
    private dev.medirian.render.font.FontGfx fontGfx;
    private final ConcurrentLinkedQueue<Runnable> tasks = new ConcurrentLinkedQueue<Runnable>();
    private final long launchedAtMs = System.currentTimeMillis();
    private LauncherBridge bridge;
    private long worldJoinedAtMs;
    private String lastStatus = "";
    private boolean wasInWorld;
    private int lastHurtTime;
    private boolean wasDead;

    private Medirian(Platform platform) {
        this.platform = platform;
        this.home = MedirianHome.resolve();
        this.modules = new ModuleManager(events, platform::supports);
        this.performance = new PerformanceManager(modules, global, profileSettings);
        this.hud = new HudManager(modules, profileSettings);
        this.config = new ConfigManager(home, modules, global, profileSettings);
        this.cosmetics = new CosmeticsManager(home);
        this.services = new dev.medirian.services.MedirianServices(dev.medirian.services.ServicesConfig.fromEnvironment(),
                platform.identity(), platform::accessToken);
        this.cloudProfiles = new dev.medirian.services.CloudProfiles(services, config, this::runOnClientThread);
        this.emotes = new dev.medirian.cosmetics.emote.EmoteManager(cosmetics, services);
        this.policies = new ServerPolicyManager(modules, notifications);
        events.subscribe(Events.WorldJoin.class, e -> onServerJoin(e.serverAddress));
        events.subscribe(Events.WorldLeave.class, e -> policies.clear());
        // Screens opened by a key are deferred to the next client tick: the game keeps dispatching the
        // current key event after our hook, and a screen opened immediately would receive the same
        // press (and close itself again, since the mod menu key also closes the menu).
        this.keybinds = new KeybindManager(modules, global, new KeybindManager.GlobalActions() {
            @Override
            public void openModMenu() {
                runOnClientThread(Medirian.this::openModMenu);
            }

            @Override
            public void openHudEditor() {
                runOnClientThread(Medirian.this::openHudEditor);
            }

            @Override
            public void playEmote() {
                runOnClientThread(() -> {
                    if (!emotes.playEquipped() && cosmetics.canRender(dev.medirian.cosmetics.CosmeticType.EMOTE)) {
                        notifications.post(I18n.tr("notify.noEmote", "No emote chosen"),
                                I18n.tr("notify.noEmote.desc", "Pick one in Cosmetics → Emotes"));
                    }
                });
            }
        });
    }

    /** Boots Medirian. Must be called on the client thread once Minecraft is initialised. */
    public static synchronized Medirian boot(Platform platform) {
        if (instance != null) {
            return instance;
        }
        long start = System.nanoTime();
        Medirian medirian = new Medirian(platform);
        instance = medirian;
        medirian.init();
        Log.info("Medirian {} ready for Minecraft {} in {} ms (home: {})", BuildInfo.VERSION,
                platform.minecraftVersion(), (System.nanoTime() - start) / 1_000_000, medirian.home);
        return medirian;
    }

    private void init() {
        BuiltinModules.registerAll(modules);
        hud.refreshElements();
        Hooks.bind(this);
        notifications.setEnabled(false); // no toasts for the initial load
        config.init(System.getProperty("medirian.profile"));
        notifications.setEnabled(global.notifications.on());
        global.notifications.onChange(notifications::setEnabled);
        performance.updateAnimations();
        cosmetics.load();
        cosmetics.registerProvider(new dev.medirian.cosmetics.BundledCosmetics());
        cosmetics.connect(services);
        services.start();
        config.setProfileListener(name -> {
            events.post(new Events.ProfileLoaded(name));
            notifications.post(I18n.tr("notify.profileLoaded", "Profile loaded"), name, NotificationManager.Level.SUCCESS);
            sendProfileToLauncher(name);
        });
        events.subscribe(Events.WorldJoin.class, e -> {
            worldJoinedAtMs = System.currentTimeMillis();
            reportStatus();
        });
        events.subscribe(Events.WorldLeave.class, e -> {
            worldJoinedAtMs = 0;
            reportStatus();
        });
        connectLauncher();
    }

    public static Medirian get() {
        Medirian m = instance;
        if (m == null) {
            throw new IllegalStateException("Medirian has not been booted");
        }
        return m;
    }

    /** The running instance or null before boot. */
    public static Medirian instance() {
        return instance;
    }

    // ------------------------------------------------------------------ services

    public Platform platform() {
        return platform;
    }

    public GameView game() {
        return platform.game();
    }

    public MedirianHome home() {
        return home;
    }

    public EventBus events() {
        return events;
    }

    public ModuleManager modules() {
        return modules;
    }

    public HudManager hud() {
        return hud;
    }

    public ConfigManager config() {
        return config;
    }

    public GlobalSettings settings() {
        return global;
    }

    public ProfileSettings profileSettings() {
        return profileSettings;
    }

    public PerformanceManager performance() {
        return performance;
    }

    public ServerPolicyManager policies() {
        return policies;
    }

    /** Announces Medirian's plugin channels so servers know they can send a {@link ServerPolicy}. */
    private void onServerJoin(String serverAddress) {
        if (serverAddress == null) {
            return;
        }
        try {
            platform.actions().registerPluginChannels(Arrays.asList(ServerPolicy.CHANNEL, ServerPolicy.HELLO_CHANNEL));
            platform.actions().sendPluginMessage(ServerPolicy.HELLO_CHANNEL,
                    ServerPolicy.hello(BuildInfo.VERSION, platform.minecraftVersion()));
        } catch (RuntimeException e) {
            Log.warn("Could not announce Medirian's plugin channels: {}", e.toString());
        }
    }

    public NotificationManager notifications() {
        return notifications;
    }

    public InputStats inputStats() {
        return inputStats;
    }

    public KeybindManager keybinds() {
        return keybinds;
    }

    public CosmeticsManager cosmetics() {
        return cosmetics;
    }

    public MedirianAccountService account() {
        return services;
    }

    public dev.medirian.services.MedirianServices services() {
        return services;
    }

    public dev.medirian.services.CloudProfiles cloudProfiles() {
        return cloudProfiles;
    }

    public dev.medirian.cosmetics.emote.EmoteManager emotes() {
        return emotes;
    }

    public boolean launcherConnected() {
        return bridge != null && bridge.connected();
    }

    public long launchedAtMs() {
        return launchedAtMs;
    }

    /** Time the current world/server was joined, or 0 when not in a world. */
    public long worldJoinedAtMs() {
        return worldJoinedAtMs;
    }

    /** Runs {@code task} on the client thread during the next tick (thread-safe). */
    public void runOnClientThread(Runnable task) {
        tasks.add(task);
    }

    // ------------------------------------------------------------------ lifecycle (called via Hooks)

    /** Called by {@link Hooks#clientTick()}. */
    public void tick() {
        Runnable task;
        while ((task = tasks.poll()) != null) {
            try {
                task.run();
            } catch (Throwable t) {
                Log.error("Scheduled task failed", t);
            }
        }
        long now = System.currentTimeMillis();
        detectStateChanges();
        performance.onTick(now);
        events.post(Events.Tick.INSTANCE);
        hud.tick();
        trails.tick(platform.game(), platform.actions(), cosmetics);
        emotes.tick(platform.game(), platform.actions());
        config.tick(now);
    }

    /**
     * Derives world join/leave, "player hurt" and "player died" events from game state, so every version adapter
     * gets them without dedicated hooks.
     */
    private void detectStateChanges() {
        GameView game = platform.game();
        boolean inWorld = game.inWorld();
        if (inWorld && !wasInWorld) {
            events.post(new Events.WorldJoin(game.isSingleplayer() ? null : game.serverAddress()));
        } else if (!inWorld && wasInWorld) {
            events.post(Events.WorldLeave.INSTANCE);
        }
        wasInWorld = inWorld;
        PlayerView player = game.player();
        int hurtTime = player == null ? 0 : player.hurtTime();
        if (hurtTime > lastHurtTime) {
            events.post(Events.PlayerHurt.INSTANCE);
        }
        lastHurtTime = hurtTime;
        boolean dead = player != null && !player.isAlive();
        if (dead && !wasDead) {
            events.post(Events.PlayerDeath.INSTANCE);
        }
        wasDead = dead;
    }

    /** Draws the in-game HUD. {@code g} is in Minecraft GUI space. */
    /**
     * The Gfx to draw Medirian's UI with: the version's, or one drawing text with Medirian's font
     * when that is chosen (and available on this runtime).
     */
    public Gfx uiGfx(Gfx g) {
        if (global.font.get() != dev.medirian.render.font.UiFont.MEDIRIAN) {
            return g;
        }
        if (!fontLoaded) {
            fontLoaded = true;
            font = dev.medirian.render.font.MedirianFont.load();
        }
        if (font == null) {
            return g;
        }
        if (fontGfx == null || fontGfx.delegate() != g) {
            fontGfx = new dev.medirian.render.font.FontGfx(font, g);
        }
        return fontGfx;
    }

    public void renderHud(Gfx g, float partialTicks) {
        g = uiGfx(g);
        long start = System.nanoTime();
        GameView game = platform.game();
        boolean hidden = game.hudHidden() || (game.debugOverlay() && global.hideHudInDebug.on());
        if (!hidden) {
            Events.RenderHud event = Events.RenderHud.INSTANCE;
            event.gfx = g;
            event.partialTicks = partialTicks;
            events.post(event);
            if (!(platform.currentScreen() instanceof HudEditorScreen)) {
                hud.render(g, false);
            }
        }
        if (platform.currentScreen() == null) {
            renderOverlay(g);
        }
        performance.recordHudRender(System.nanoTime() - start);
    }

    /** Draws notifications in Medirian's virtual space. Also called by Medirian screens. */
    public void renderOverlay(Gfx g) {
        float factor = UiScale.factor(g.guiScale(), g.height());
        g.push();
        g.scale(factor, factor);
        notifications.render(g, g.width() / factor);
        g.pop();
    }

    /** Called by {@link Hooks#shutdown()}. */
    public void shutdown() {
        config.shutdown();
        services.shutdown();
        if (bridge != null) {
            bridge.close();
        }
    }

    // ------------------------------------------------------------------ screens

    public void openModMenu() {
        keybinds.releaseAll();
        platform.openScreen(new ModMenuScreen(null));
    }

    public void openHudEditor() {
        keybinds.releaseAll();
        platform.openScreen(new HudEditorScreen(null));
    }

    public void openSettings() {
        keybinds.releaseAll();
        platform.openScreen(new SettingsScreen(null));
    }

    // ------------------------------------------------------------------ launcher bridge

    private void connectLauncher() {
        bridge = LauncherBridge.fromSystemProperties();
        if (bridge == null) {
            return;
        }
        JsonObject hello = new JsonObject();
        hello.addProperty("clientVersion", BuildInfo.VERSION);
        hello.addProperty("target", platform.targetId());
        hello.addProperty("minecraft", platform.minecraftVersion());
        hello.addProperty("profile", config.activeProfile());
        hello.addProperty("player", platform.identity().name());
        bridge.start(hello, message -> runOnClientThread(() -> handleLauncherMessage(message)));
    }

    private void handleLauncherMessage(JsonObject message) {
        String type = message.has("type") ? message.get("type").getAsString() : "";
        if ("notify".equals(type)) {
            String title = message.has("title") ? message.get("title").getAsString() : "Medirian";
            String body = message.has("message") ? message.get("message").getAsString() : null;
            NotificationManager.Level level = NotificationManager.Level.INFO;
            if (message.has("level")) {
                try {
                    level = NotificationManager.Level.valueOf(message.get("level").getAsString().toUpperCase());
                } catch (IllegalArgumentException ignored) {
                    // keep INFO
                }
            }
            notifications.post(title, body, level);
        }
    }

    private void reportStatus() {
        if (bridge == null) {
            return;
        }
        GameView game = platform.game();
        String state = !game.inWorld() ? "menu" : game.isSingleplayer() ? "singleplayer" : "multiplayer";
        String server = game.isSingleplayer() ? null : game.serverAddress();
        String key = state + "|" + server;
        if (key.equals(lastStatus)) {
            return;
        }
        lastStatus = key;
        JsonObject status = new JsonObject();
        status.addProperty("type", "status");
        status.addProperty("state", state);
        if (server != null) {
            status.addProperty("server", server);
        }
        bridge.send(status);
    }

    private void sendProfileToLauncher(String name) {
        if (bridge == null) {
            return;
        }
        JsonObject message = new JsonObject();
        message.addProperty("type", "profile");
        message.addProperty("name", name);
        bridge.send(message);
    }
}
