package dev.meridian.ui;

import dev.meridian.account.MeridianAccountService;
import dev.meridian.account.PlayerIdentity;
import dev.meridian.config.ConfigManager;
import dev.meridian.config.GlobalSettings;
import dev.meridian.core.BuildInfo;
import dev.meridian.core.Meridian;
import dev.meridian.i18n.I18n;
import dev.meridian.input.Key;
import dev.meridian.notify.NotificationManager;
import dev.meridian.perf.FrameStats;
import dev.meridian.perf.PerformanceManager;
import dev.meridian.perf.SystemStats;
import dev.meridian.platform.ClientActions;
import dev.meridian.render.Anim;
import dev.meridian.render.Colors;
import dev.meridian.render.Gfx;
import dev.meridian.render.Theme;
import dev.meridian.render.UiDraw;
import dev.meridian.ui.widget.Button;
import dev.meridian.ui.widget.Slider;
import dev.meridian.ui.widget.Switch;
import dev.meridian.ui.widget.TextField;
import dev.meridian.ui.widget.Widget;
import dev.meridian.ui.widget.WidgetRow;
import dev.meridian.util.Format;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Global Meridian settings: General, HUD, Game, Performance, Profiles and Account. */
public final class SettingsScreen extends MeridianScreen {

    /** Settings pages. */
    public enum Tab { GENERAL, HUD, GAME, PERFORMANCE, PROFILES, ACCOUNT }

    private static final float NAV_W = 124;
    private static Tab lastTab = Tab.GENERAL;

    private Tab tab = lastTab;
    private float px;
    private float py;
    private float pw;
    private float ph;
    private SettingsList list;
    private Button backButton;
    private TextField newProfileName;
    private String profileError;
    private long confirmResetUntil;
    private int lastRealWidth;
    private int lastRealHeight;
    private String pendingDelete;
    private final Map<Tab, Anim> navAnims = new HashMap<Tab, Anim>();

    public SettingsScreen(MeridianScreen parent) {
        super(parent);
    }

    @Override
    protected void init() {
        pw = Math.min(600, width - 32);
        ph = Math.min(370, height - 32);
        px = (width - pw) / 2f;
        py = (height - ph) / 2f;
        backButton = new Button(parent != null ? I18n.tr("ui.back", "Back") : I18n.tr("ui.done", "Done"),
                Button.Style.SECONDARY, this::close).bounds(px + 10, py + ph - 26, NAV_W - 20, 16);
        buildTab();
    }

    private String tabName(Tab t) {
        switch (t) {
            case GENERAL: return I18n.tr("settings.tab.general", "General");
            case HUD: return I18n.tr("settings.tab.hud", "HUD");
            case GAME: return I18n.tr("settings.tab.game", "Game");
            case PERFORMANCE: return I18n.tr("settings.tab.performance", "Performance");
            case PROFILES: return I18n.tr("settings.tab.profiles", "Profiles");
            default: return I18n.tr("settings.tab.account", "Account");
        }
    }

    private void buildTab() {
        list = new SettingsList(this);
        list.bounds(px + NAV_W + 14, py + 36, pw - NAV_W - 24, ph - 46);
        Meridian meridian = Meridian.get();
        GlobalSettings global = meridian.settings();
        switch (tab) {
            case GENERAL:
                list.addSettings(java.util.Arrays.<dev.meridian.setting.Setting<?>>asList(
                        global.language, global.theme, global.notifications, global.animations));
                list.add(new SettingsList.HeaderRow(I18n.tr("settings.keys", "Keys")));
                list.addSettings(java.util.Arrays.<dev.meridian.setting.Setting<?>>asList(global.modMenuKey, global.hudEditorKey));
                break;
            case HUD:
                list.addSettings(java.util.Arrays.<dev.meridian.setting.Setting<?>>asList(
                        meridian.profileSettings().hudScale, global.hideHudInDebug));
                list.add(new SettingsList.ControlRow(I18n.tr("settings.hud.editor", "HUD editor"),
                        I18n.tr("settings.hud.editor.desc", "Move, scale, add and remove HUD elements"),
                        new Button(I18n.tr("ui.open", "Open"), Button.Style.PRIMARY,
                                () -> meridian.platform().openScreen(new HudEditorScreen(this))), 72));
                list.add(new SettingsList.ControlRow(I18n.tr("settings.hud.reset", "Reset HUD"),
                        I18n.tr("settings.hud.reset.desc", "Restore default positions and scale of every element"),
                        new Button(() -> System.currentTimeMillis() < confirmResetUntil
                                ? I18n.tr("ui.confirm", "Confirm") : I18n.tr("ui.reset", "Reset"), Button.Style.DANGER, () -> {
                            if (System.currentTimeMillis() < confirmResetUntil) {
                                meridian.config().resetHudLayout();
                                confirmResetUntil = 0;
                                meridian.notifications().post(I18n.tr("notify.hudReset", "HUD layout reset"), null);
                            } else {
                                confirmResetUntil = System.currentTimeMillis() + 3000;
                            }
                        }), 72));
                list.add(new SettingsList.InfoRow(I18n.tr("settings.hud.cost", "HUD render cost"),
                        () -> Format.decimals(meridian.performance().hudRenderMs(), 3) + " ms / frame"));
                break;
            case GAME:
                buildGameTab(meridian);
                break;
            case PERFORMANCE:
                buildPerformanceTab(meridian);
                break;
            case PROFILES:
                buildProfilesTab(meridian);
                break;
            default:
                buildAccountTab(meridian);
                break;
        }
    }

    private void buildGameTab(final Meridian meridian) {
        final ClientActions actions = meridian.platform().actions();
        list.add(new SettingsList.ControlRow(I18n.tr("settings.game.vanilla", "Minecraft settings"),
                I18n.tr("settings.game.vanilla.desc", "Video, audio, controls and other vanilla options"),
                new Button(I18n.tr("ui.open", "Open"), Button.Style.SECONDARY, actions::openVanillaSettings), 72));
        list.add(new SettingsList.ControlRow(I18n.tr("settings.game.fullscreen", "Fullscreen"), null,
                new Switch(actions::isFullscreen, on -> actions.toggleFullscreen()), 22));
        final int unlimited = actions.unlimitedFps();
        Slider fps = new Slider(new Slider.Model() {
            @Override
            public double progress() {
                return (Math.min(unlimited, actions.maxFps()) - 10) / (double) (unlimited - 10);
            }

            @Override
            public void setProgress(double progress) {
                int value = (int) Math.round((10 + progress * (unlimited - 10)) / 10.0) * 10;
                actions.setMaxFps(Math.max(10, Math.min(unlimited, value)));
            }

            @Override
            public String label() {
                return actions.maxFps() >= unlimited ? I18n.tr("settings.game.unlimited", "Unlimited") : String.valueOf(actions.maxFps());
            }

            @Override
            public void nudge(int steps) {
                actions.setMaxFps(Math.max(10, Math.min(unlimited, actions.maxFps() + steps * 10)));
            }
        });
        list.add(new SettingsList.ControlRow(I18n.tr("settings.game.fpsLimit", "FPS limit"), null, fps, 150));
        list.add(new SettingsList.InfoRow(I18n.tr("settings.game.window", "Window size"),
                () -> lastRealWidth + " × " + lastRealHeight));
        list.add(new SettingsList.InfoRow(I18n.tr("settings.game.resolutionHint", "Startup resolution"),
                () -> I18n.tr("settings.game.resolutionHint.value", "set in the launcher profile")));
    }


    private void buildPerformanceTab(final Meridian meridian) {
        final PerformanceManager perf = meridian.performance();
        list.addSettings(java.util.Collections.<dev.meridian.setting.Setting<?>>singletonList(meridian.profileSettings().performanceProfile));
        list.add(new SettingsList.ControlRow(I18n.tr("settings.perf.video", "Recommended video settings"),
                I18n.tr("settings.perf.video.desc", "Changes vanilla options (clouds, particles, shadows…) for the selected mode"),
                new Button(I18n.tr("ui.apply", "Apply"), Button.Style.SECONDARY, () -> {
                    ClientActions.VideoPreset preset = ClientActions.VideoPreset.valueOf(perf.profile().name());
                    List<String> changes = meridian.platform().actions().applyVideoPreset(preset);
                    String message = changes.isEmpty() ? I18n.tr("notify.video.none", "Already optimal")
                            : String.join(", ", changes);
                    meridian.notifications().post(I18n.tr("notify.video", "Video settings applied"), message,
                            NotificationManager.Level.SUCCESS);
                }), 72));
        list.add(new SettingsList.HeaderRow(I18n.tr("settings.perf.monitor", "Live monitor")));
        final FrameStats frames = perf.frames();
        final SystemStats system = perf.system();
        list.add(new SettingsList.InfoRow("FPS", () -> Math.round(frames.averageFps()) + "  (1% low "
                + Math.round(frames.onePercentLowFps()) + ")"));
        list.add(new SettingsList.InfoRow(I18n.tr("settings.perf.frametime", "Frame time"),
                () -> Format.decimals(frames.averageFrameMs(), 2) + " ms"));
        list.add(new SettingsList.InfoRow(I18n.tr("settings.perf.world", "World render time"),
                () -> perf.worldRenderMs() > 0 ? Format.decimals(perf.worldRenderMs(), 2) + " ms" : "-"));
        list.add(new SettingsList.InfoRow(I18n.tr("settings.perf.hud", "Meridian HUD cost"),
                () -> Format.decimals(perf.hudRenderMs(), 3) + " ms"));
        list.add(new SettingsList.InfoRow(I18n.tr("settings.perf.memory", "Memory"),
                () -> Format.bytes(system.usedBytes()) + " / " + Format.bytes(system.maxBytes())
                        + "  (" + Math.round(system.memoryPercent()) + "%)"));
        list.add(new SettingsList.InfoRow(I18n.tr("settings.perf.memoryHint", "Allocated memory"),
                () -> I18n.tr("settings.perf.memoryHint.value", "change RAM in the launcher profile")));
        list.add(new SettingsList.InfoRow("CPU", () -> system.cpuLoad() < 0 ? "-" : Math.round(system.cpuLoad() * 100) + "%"));
    }

    private void buildProfilesTab(final Meridian meridian) {
        final ConfigManager config = meridian.config();
        list.add(new SettingsList.InfoRow(I18n.tr("settings.profiles.active", "Active profile"), config::activeProfile));
        list.add(new SettingsList.HeaderRow(I18n.tr("settings.profiles.all", "Profiles")));
        for (final String name : config.profiles()) {
            boolean active = name.equalsIgnoreCase(config.activeProfile());
            Button load = new Button(active ? I18n.tr("settings.profiles.loaded", "Active") : I18n.tr("settings.profiles.load", "Load"),
                    active ? Button.Style.GHOST : Button.Style.PRIMARY, () -> {
                        if (config.loadProfile(name, true)) {
                            buildTab();
                        }
                    }).enabled(!active);
            Button delete = new Button(() -> name.equals(pendingDelete) ? I18n.tr("ui.confirm", "Confirm") : I18n.tr("ui.delete", "Delete"),
                    Button.Style.DANGER, () -> {
                        if (name.equals(pendingDelete)) {
                            pendingDelete = null;
                            if (config.deleteProfile(name)) {
                                meridian.notifications().post(I18n.tr("notify.profileDeleted", "Profile deleted"), name);
                            }
                            buildTab();
                        } else {
                            pendingDelete = name;
                        }
                    }).enabled(!ConfigManager.DEFAULT_PROFILE.equalsIgnoreCase(name));
            WidgetRow buttons = new WidgetRow(new Widget[] {load, delete}, new float[] {56, 56});
            list.add(new SettingsList.ControlRow(name, null, buttons, buttons.preferredWidth()));
        }
        list.add(new SettingsList.HeaderRow(I18n.tr("settings.profiles.new", "New profile")));
        newProfileName = new TextField(this, "", 24).placeholder(I18n.tr("settings.profiles.name", "Profile name"))
                .onChange(text -> profileError = null).onSubmit(() -> createProfile(meridian));
        Button create = new Button(I18n.tr("ui.create", "Create"), Button.Style.PRIMARY, () -> createProfile(meridian));
        WidgetRow row = new WidgetRow(new Widget[] {newProfileName, create}, new float[] {150, 56});
        list.add(new SettingsList.ControlRow(I18n.tr("settings.profiles.copy", "Copy of current settings"),
                null, row, row.preferredWidth()));
        list.add(new SettingsList.InfoRow("", () -> profileError == null ? "" : profileError));
    }

    private void createProfile(Meridian meridian) {
        String name = newProfileName.text();
        String error = meridian.config().validateName(name);
        if (error != null) {
            profileError = I18n.tr(error, errorFallback(error));
            return;
        }
        meridian.config().createProfile(name);
        meridian.config().loadProfile(name.trim(), true);
        buildTab();
    }

    private static String errorFallback(String key) {
        if (key.endsWith("empty")) {
            return "Enter a name";
        }
        if (key.endsWith("long")) {
            return "Name is too long (max 24)";
        }
        if (key.endsWith("chars")) {
            return "Use letters, digits, spaces, - and _";
        }
        return "A profile with this name exists";
    }

    private void buildAccountTab(final Meridian meridian) {
        final PlayerIdentity identity = meridian.platform().identity();
        list.add(new SettingsList.HeaderRow(I18n.tr("settings.account.minecraft", "Minecraft account")));
        list.add(new SettingsList.InfoRow(I18n.tr("settings.account.name", "Player"), identity::name));
        list.add(new SettingsList.InfoRow("UUID", identity::uuid));
        list.add(new SettingsList.InfoRow(I18n.tr("settings.account.session", "Session"),
                () -> identity.online() ? I18n.tr("settings.account.online", "Microsoft account")
                        : I18n.tr("settings.account.offline", "Offline (development)")));
        list.add(new SettingsList.HeaderRow(I18n.tr("settings.account.meridian", "Meridian account")));
        list.add(new SettingsList.InfoRow(I18n.tr("settings.account.status", "Status"), () -> {
            MeridianAccountService.State state = meridian.account().state();
            if (state == MeridianAccountService.State.SIGNED_IN) {
                return meridian.account().displayName();
            }
            return state == MeridianAccountService.State.SIGNED_OUT
                    ? I18n.tr("settings.account.signedOut", "Signed out")
                    : I18n.tr("settings.account.unavailable", "Not available yet");
        }));
        list.add(new SettingsList.HeaderRow(I18n.tr("settings.account.client", "Client")));
        list.add(new SettingsList.InfoRow(I18n.tr("settings.account.version", "Meridian"),
                () -> BuildInfo.VERSION + " · Minecraft " + meridian.platform().minecraftVersion()));
        list.add(new SettingsList.InfoRow(I18n.tr("settings.account.launcher", "Launcher"),
                () -> meridian.launcherConnected() ? I18n.tr("settings.account.connected", "Connected")
                        : I18n.tr("settings.account.notConnected", "Not started from the launcher")));
        list.add(new SettingsList.InfoRow(I18n.tr("settings.account.home", "Data folder"), () -> meridian.home().root().getName()));
    }

    // ------------------------------------------------------------------ rendering

    @Override
    protected void render(Gfx g, float mx, float my, float delta) {
        lastRealWidth = (int) Math.round(g.width() * g.guiScale());
        lastRealHeight = (int) Math.round(g.height() * g.guiScale());
        Theme theme = Theme.current();
        renderBackdrop(g);
        UiDraw.shadow(g, px, py, pw, ph, 8, 4);
        UiDraw.roundRectBordered(g, px, py, pw, ph, 8, theme.panel, theme.border);
        UiDraw.roundRect(g, px + 1, py + 1, NAV_W, ph - 2, 7, theme.surface);
        g.fill((int) (px + NAV_W), (int) py + 1, (int) (px + NAV_W) + 1, (int) (py + ph) - 1, theme.border);

        g.text("§l" + I18n.tr("ui.settings", "Settings"), px + 12, py + 14, theme.text, false);
        float y = py + 36;
        for (Tab t : Tab.values()) {
            boolean active = t == tab;
            boolean hovered = mx >= px + 8 && mx < px + NAV_W - 8 && my >= y && my < y + 17;
            Anim anim = navAnims.get(t);
            if (anim == null) {
                anim = new Anim(0f, 18f);
                navAnims.put(t, anim);
            }
            float h = anim.target(hovered ? 1f : 0f).get();
            if (active) {
                UiDraw.roundRect(g, px + 8, y, NAV_W - 16, 17, 3, theme.accentSoft);
                UiDraw.roundRect(g, px + 8, y + 4, 2, 9, 1, theme.accent);
            } else if (h > 0.01f) {
                UiDraw.roundRect(g, px + 8, y, NAV_W - 16, 17, 3, Colors.fade(theme.elevated, h));
            }
            g.text(tabName(t), px + 17, y + 5, active ? theme.text : Colors.lerp(theme.textDim, theme.text, h), false);
            y += 19;
        }
        backButton.render(g, mx, my);

        g.text("§l" + tabName(tab), px + NAV_W + 14, py + 14, theme.text, false);
        g.fill((int) (px + NAV_W + 14), (int) py + 28, (int) (px + pw) - 10, (int) py + 29, theme.border);
        list.render(g, mx, my);
    }

    // ------------------------------------------------------------------ input

    @Override
    protected boolean mouseClicked(float mx, float my, int button) {
        float y = py + 36;
        if (mx >= px + 8 && mx < px + NAV_W - 8) {
            for (Tab t : Tab.values()) {
                if (my >= y && my < y + 17) {
                    tab = t;
                    lastTab = t;
                    pendingDelete = null;
                    buildTab();
                    return true;
                }
                y += 19;
            }
        }
        if (backButton.mouseClicked(mx, my, button)) {
            return true;
        }
        return list.mouseClicked(mx, my, button);
    }

    @Override
    protected boolean mouseReleased(float mx, float my, int button) {
        list.mouseReleased(mx, my, button);
        return true;
    }

    @Override
    protected boolean mouseDragged(float mx, float my, int button) {
        list.mouseDragged(mx, my);
        return true;
    }

    @Override
    protected boolean mouseScrolled(float mx, float my, double amount) {
        return list.mouseScrolled(mx, my, amount);
    }

    @Override
    protected boolean keyPressed(Key key, boolean ctrl, boolean shift) {
        return list.keyPressed(key, ctrl, shift, mouseX(), mouseY());
    }
}
