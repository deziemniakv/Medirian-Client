package dev.meridian.core;

import com.google.gson.JsonObject;
import dev.meridian.account.MeridianAccountService;
import dev.meridian.config.ConfigManager;
import dev.meridian.config.GlobalSettings;
import dev.meridian.config.ProfileSettings;
import dev.meridian.cosmetics.CosmeticsManager;
import dev.meridian.event.EventBus;
import dev.meridian.event.Events;
import dev.meridian.hud.HudManager;
import dev.meridian.i18n.I18n;
import dev.meridian.input.InputStats;
import dev.meridian.input.KeybindManager;
import dev.meridian.ipc.LauncherBridge;
import dev.meridian.module.BuiltinModules;
import dev.meridian.module.ModuleManager;
import dev.meridian.notify.NotificationManager;
import dev.meridian.perf.PerformanceManager;
import dev.meridian.platform.GameView;
import dev.meridian.platform.Hooks;
import dev.meridian.platform.Platform;
import dev.meridian.platform.PlayerView;
import dev.meridian.render.Gfx;
import dev.meridian.ui.HudEditorScreen;
import dev.meridian.ui.ModMenuScreen;
import dev.meridian.ui.SettingsScreen;
import dev.meridian.ui.UiScale;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Entry point and service locator of Meridian. A version adapter calls {@link #boot(Platform)}
 * once the game client is initialised; after that the adapter only talks to {@link Hooks}.
 */
public final class Meridian {

    private static volatile Meridian instance;

    private final Platform platform;
    private final MeridianHome home;
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
    private final MeridianAccountService account = MeridianAccountService.UNAVAILABLE;
    private final ConcurrentLinkedQueue<Runnable> tasks = new ConcurrentLinkedQueue<Runnable>();
    private final long launchedAtMs = System.currentTimeMillis();
    private LauncherBridge bridge;
    private long worldJoinedAtMs;
    private String lastStatus = "";
    private boolean wasInWorld;
    private int lastHurtTime;
    private boolean wasDead;

    private Meridian(Platform platform) {
        this.platform = platform;
        this.home = MeridianHome.resolve();
        this.modules = new ModuleManager(events, platform::supports);
        this.performance = new PerformanceManager(modules, global, profileSettings);
        this.hud = new HudManager(modules, profileSettings);
        this.config = new ConfigManager(home, modules, global, profileSettings);
        this.cosmetics = new CosmeticsManager(home);
        // Screens opened by a key are deferred to the next client tick: the game keeps dispatching the
        // current key event after our hook, and a screen opened immediately would receive the same
        // press (and close itself again, since the mod menu key also closes the menu).
        this.keybinds = new KeybindManager(modules, global, new KeybindManager.GlobalActions() {
            @Override
            public void openModMenu() {
                runOnClientThread(Meridian.this::openModMenu);
            }

            @Override
            public void openHudEditor() {
                runOnClientThread(Meridian.this::openHudEditor);
            }
        });
    }

    /** Boots Meridian. Must be called on the client thread once Minecraft is initialised. */
    public static synchronized Meridian boot(Platform platform) {
        if (instance != null) {
            return instance;
        }
        long start = System.nanoTime();
        Meridian meridian = new Meridian(platform);
        instance = meridian;
        meridian.init();
        Log.info("Meridian {} ready for Minecraft {} in {} ms (home: {})", BuildInfo.VERSION,
                platform.minecraftVersion(), (System.nanoTime() - start) / 1_000_000, meridian.home);
        return meridian;
    }

    private void init() {
        BuiltinModules.registerAll(modules);
        hud.refreshElements();
        Hooks.bind(this);
        notifications.setEnabled(false); // no toasts for the initial load
        config.init(System.getProperty("meridian.profile"));
        notifications.setEnabled(global.notifications.on());
        global.notifications.onChange(notifications::setEnabled);
        performance.updateAnimations();
        cosmetics.load();
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

    public static Meridian get() {
        Meridian m = instance;
        if (m == null) {
            throw new IllegalStateException("Meridian has not been booted");
        }
        return m;
    }

    /** The running instance or null before boot. */
    public static Meridian instance() {
        return instance;
    }

    // ------------------------------------------------------------------ services

    public Platform platform() {
        return platform;
    }

    public GameView game() {
        return platform.game();
    }

    public MeridianHome home() {
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

    public MeridianAccountService account() {
        return account;
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
    public void renderHud(Gfx g, float partialTicks) {
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

    /** Draws notifications in Meridian's virtual space. Also called by Meridian screens. */
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
            String title = message.has("title") ? message.get("title").getAsString() : "Meridian";
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
