package dev.medirian.ui;

import dev.medirian.account.MedirianAccountService;
import dev.medirian.account.PlayerIdentity;
import dev.medirian.config.ConfigManager;
import dev.medirian.config.GlobalSettings;
import dev.medirian.core.BuildInfo;
import dev.medirian.core.Medirian;
import dev.medirian.i18n.I18n;
import dev.medirian.input.Key;
import dev.medirian.notify.NotificationManager;
import dev.medirian.perf.FrameStats;
import dev.medirian.perf.PerformanceManager;
import dev.medirian.perf.SystemStats;
import dev.medirian.platform.ClientActions;
import dev.medirian.render.Anim;
import dev.medirian.render.Colors;
import dev.medirian.render.Gfx;
import dev.medirian.render.Pixel;
import dev.medirian.render.Theme;
import dev.medirian.render.UiDraw;
import dev.medirian.services.CloudProfiles;
import dev.medirian.ui.widget.Button;
import dev.medirian.ui.widget.Slider;
import dev.medirian.ui.widget.Switch;
import dev.medirian.ui.widget.TextField;
import dev.medirian.ui.widget.Widget;
import dev.medirian.ui.widget.WidgetRow;
import dev.medirian.util.Format;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Global Medirian settings: General, HUD, Game, Performance, Profiles and Account. */
public final class SettingsScreen extends MedirianScreen {

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
    private String pendingCloudDelete;
    /** Profiles in the Medirian cloud; null until loaded. */
    private List<CloudProfiles.Entry> cloud;
    private String cloudError;
    private boolean cloudLoading;
    private boolean closed;
    private final Map<Tab, Anim> navAnims = new HashMap<Tab, Anim>();

    public SettingsScreen(MedirianScreen parent) {
        super(parent);
    }

    @Override
    protected void init() {
        pw = Math.min(600, width - 32);
        ph = Math.min(370, height - 32);
        px = (width - pw) / 2f;
        py = (height - ph) / 2f;
        backButton = new Button(parent != null ? I18n.tr("ui.back", "Back") : I18n.tr("ui.done", "Done"),
                Button.Style.SECONDARY, this::close).icon("back").bounds(px + 6, py + ph - 26, NAV_W - 6, 18);
        buildTab();
    }

    private static String tabIcon(Tab t) {
        switch (t) {
            case GENERAL: return "settings";
            case HUD: return "cat-hud";
            case GAME: return "singleplayer";
            case PERFORMANCE: return "cat-performance";
            case PROFILES: return "profiles";
            default: return "cat-player";
        }
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
        list.bounds(px + NAV_W + 14, py + 38, pw - NAV_W - 24, ph - 48);
        Medirian medirian = Medirian.get();
        GlobalSettings global = medirian.settings();
        switch (tab) {
            case GENERAL:
                list.addSettings(java.util.Arrays.<dev.medirian.setting.Setting<?>>asList(
                        global.language, global.mainMenu, global.font, global.notifications, global.animations, global.winterSnow));
                list.add(new SettingsList.HeaderRow(I18n.tr("settings.keys", "Keys")));
                list.addSettings(java.util.Arrays.<dev.medirian.setting.Setting<?>>asList(global.modMenuKey, global.hudEditorKey, global.emoteKey));
                break;
            case HUD:
                list.addSettings(java.util.Arrays.<dev.medirian.setting.Setting<?>>asList(
                        medirian.profileSettings().hudScale, global.hideHudInDebug));
                list.add(new SettingsList.ControlRow(I18n.tr("settings.hud.editor", "HUD editor"),
                        I18n.tr("settings.hud.editor.desc", "Move, scale, add and remove HUD elements"),
                        new Button(I18n.tr("ui.open", "Open"), Button.Style.PRIMARY,
                                () -> medirian.platform().openScreen(new HudEditorScreen(this))), 72));
                list.add(new SettingsList.ControlRow(I18n.tr("settings.hud.reset", "Reset HUD"),
                        I18n.tr("settings.hud.reset.desc", "Restore default positions and scale of every element"),
                        new Button(() -> System.currentTimeMillis() < confirmResetUntil
                                ? I18n.tr("ui.confirm", "Confirm") : I18n.tr("ui.reset", "Reset"), Button.Style.DANGER, () -> {
                            if (System.currentTimeMillis() < confirmResetUntil) {
                                medirian.config().resetHudLayout();
                                confirmResetUntil = 0;
                                medirian.notifications().post(I18n.tr("notify.hudReset", "HUD layout reset"), null);
                            } else {
                                confirmResetUntil = System.currentTimeMillis() + 3000;
                            }
                        }), 72));
                list.add(new SettingsList.InfoRow(I18n.tr("settings.hud.cost", "HUD render cost"),
                        () -> Format.decimals(medirian.performance().hudRenderMs(), 3) + " ms / frame"));
                break;
            case GAME:
                buildGameTab(medirian);
                break;
            case PERFORMANCE:
                buildPerformanceTab(medirian);
                break;
            case PROFILES:
                buildProfilesTab(medirian);
                break;
            default:
                buildAccountTab(medirian);
                break;
        }
    }

    private void buildGameTab(final Medirian medirian) {
        final ClientActions actions = medirian.platform().actions();
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


    private void buildPerformanceTab(final Medirian medirian) {
        final PerformanceManager perf = medirian.performance();
        list.addSettings(java.util.Collections.<dev.medirian.setting.Setting<?>>singletonList(medirian.profileSettings().performanceProfile));
        list.add(new SettingsList.ControlRow(I18n.tr("settings.perf.video", "Recommended video settings"),
                I18n.tr("settings.perf.video.desc", "Changes vanilla options (clouds, particles, shadows…) for the selected mode"),
                new Button(I18n.tr("ui.apply", "Apply"), Button.Style.SECONDARY, () -> {
                    ClientActions.VideoPreset preset = ClientActions.VideoPreset.valueOf(perf.profile().name());
                    List<String> changes = medirian.platform().actions().applyVideoPreset(preset);
                    String message = changes.isEmpty() ? I18n.tr("notify.video.none", "Already optimal")
                            : String.join(", ", changes);
                    medirian.notifications().post(I18n.tr("notify.video", "Video settings applied"), message,
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
        list.add(new SettingsList.InfoRow(I18n.tr("settings.perf.hud", "Medirian HUD cost"),
                () -> Format.decimals(perf.hudRenderMs(), 3) + " ms"));
        list.add(new SettingsList.InfoRow(I18n.tr("settings.perf.memory", "Memory"),
                () -> Format.bytes(system.usedBytes()) + " / " + Format.bytes(system.maxBytes())
                        + "  (" + Math.round(system.memoryPercent()) + "%)"));
        list.add(new SettingsList.InfoRow(I18n.tr("settings.perf.memoryHint", "Allocated memory"),
                () -> I18n.tr("settings.perf.memoryHint.value", "change RAM in the launcher profile")));
        list.add(new SettingsList.InfoRow("CPU", () -> system.cpuLoad() < 0 ? "-" : Math.round(system.cpuLoad() * 100) + "%"));
    }

    private void buildProfilesTab(final Medirian medirian) {
        final ConfigManager config = medirian.config();
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
                                medirian.notifications().post(I18n.tr("notify.profileDeleted", "Profile deleted"), name);
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
                .onChange(text -> profileError = null).onSubmit(() -> createProfile(medirian));
        Button create = new Button(I18n.tr("ui.create", "Create"), Button.Style.PRIMARY, () -> createProfile(medirian));
        WidgetRow row = new WidgetRow(new Widget[] {newProfileName, create}, new float[] {150, 56});
        list.add(new SettingsList.ControlRow(I18n.tr("settings.profiles.copy", "Copy of current settings"),
                null, row, row.preferredWidth()));
        list.add(new SettingsList.InfoRow("", () -> profileError == null ? "" : profileError));
        buildCloudSection(medirian);
    }

    /** One line describing the Medirian account. */
    static String accountStatus(MedirianAccountService account) {
        switch (account.state()) {
            case SIGNED_IN:
                return I18n.tr("settings.account.signedIn", "Signed in as {0}", account.displayName());
            case SIGNING_IN:
                return I18n.tr("settings.account.signingIn", "Signing in...");
            case OFFLINE_ACCOUNT:
                return I18n.tr("settings.account.offlineAccount", "Needs a Microsoft account");
            case FAILED:
                return I18n.tr("settings.account.failed", "Sign-in failed");
            default:
                return I18n.tr("settings.account.unavailable", "Medirian services are not configured");
        }
    }

    /** The Medirian cloud part of the Profiles tab. */
    private void buildCloudSection(final Medirian medirian) {
        list.add(new SettingsList.HeaderRow(I18n.tr("settings.cloud.title", "Medirian Cloud")));
        final MedirianAccountService account = medirian.account();
        if (account.state() != MedirianAccountService.State.SIGNED_IN) {
            list.add(new SettingsList.InfoRow(I18n.tr("settings.account.status", "Status"), () -> accountStatus(account)));
            return;
        }
        final CloudProfiles cloudProfiles = medirian.cloudProfiles();
        final String active = medirian.config().activeProfile();
        list.add(new SettingsList.ControlRow(I18n.tr("settings.cloud.uploadActive", "Upload the active profile"), active,
                new Button(I18n.tr("settings.cloud.upload", "Upload"), Button.Style.PRIMARY,
                        () -> cloudProfiles.upload(active, (updatedAt, error) -> {
                            cloudResult(medirian, error, I18n.tr("notify.cloudUploaded", "Profile uploaded"), active);
                            cloud = null;
                            refreshCloud(medirian);
                        })), 72));
        if (cloud == null) {
            list.add(new SettingsList.InfoRow("", () -> cloudError != null
                    ? I18n.tr("settings.cloud.error", "Cloud unavailable: {0}", cloudError)
                    : I18n.tr("settings.cloud.loading", "Loading...")));
            refreshCloud(medirian);
            return;
        }
        if (cloud.isEmpty()) {
            list.add(new SettingsList.InfoRow("", () -> I18n.tr("settings.cloud.empty", "No profiles in the cloud yet")));
        }
        java.text.SimpleDateFormat format = new java.text.SimpleDateFormat("dd.MM.yyyy HH:mm");
        for (final CloudProfiles.Entry entry : cloud) {
            Button download = new Button(I18n.tr("settings.cloud.download", "Download"), Button.Style.SECONDARY,
                    () -> cloudProfiles.download(entry.name, (ok, error) -> {
                        cloudResult(medirian, error, I18n.tr("notify.cloudDownloaded", "Profile downloaded"), entry.name);
                        rebuildIfOpen();
                    }));
            Button delete = new Button(() -> entry.name.equals(pendingCloudDelete) ? I18n.tr("ui.confirm", "Confirm") : I18n.tr("ui.delete", "Delete"),
                    Button.Style.DANGER, () -> {
                        if (!entry.name.equals(pendingCloudDelete)) {
                            pendingCloudDelete = entry.name;
                            return;
                        }
                        pendingCloudDelete = null;
                        cloudProfiles.delete(entry.name, (ok, error) -> {
                            cloudResult(medirian, error, I18n.tr("notify.cloudDeleted", "Removed from the cloud"), entry.name);
                            cloud = null;
                            refreshCloud(medirian);
                        });
                    });
            WidgetRow buttons = new WidgetRow(new Widget[] {download, delete}, new float[] {66, 56});
            list.add(new SettingsList.ControlRow(entry.name,
                    I18n.tr("settings.cloud.saved", "Saved {0}", format.format(new java.util.Date(entry.updatedAt))),
                    buttons, buttons.preferredWidth()));
        }
    }

    private void refreshCloud(Medirian medirian) {
        if (cloudLoading) {
            return;
        }
        cloudLoading = true;
        cloudError = null;
        medirian.cloudProfiles().list((entries, error) -> {
            cloudLoading = false;
            cloud = entries;
            cloudError = error;
            rebuildIfOpen();
        });
    }

    private void cloudResult(Medirian medirian, String error, String success, String name) {
        if (error == null) {
            medirian.notifications().post(success, name, NotificationManager.Level.SUCCESS);
        } else {
            medirian.notifications().post(I18n.tr("notify.cloudFailed", "Cloud sync failed"), error, NotificationManager.Level.WARNING);
        }
    }

    private void rebuildIfOpen() {
        if (!closed && tab == Tab.PROFILES) {
            buildTab();
        }
    }

    @Override
    protected void removed() {
        closed = true;
    }

    private void createProfile(Medirian medirian) {
        String name = newProfileName.text();
        String error = medirian.config().validateName(name);
        if (error != null) {
            profileError = I18n.tr(error, errorFallback(error));
            return;
        }
        medirian.config().createProfile(name);
        medirian.config().loadProfile(name.trim(), true);
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

    private void buildAccountTab(final Medirian medirian) {
        final PlayerIdentity identity = medirian.platform().identity();
        list.add(new SettingsList.HeaderRow(I18n.tr("settings.account.minecraft", "Minecraft account")));
        list.add(new SettingsList.InfoRow(I18n.tr("settings.account.name", "Player"), identity::name));
        list.add(new SettingsList.InfoRow("UUID", identity::uuid));
        list.add(new SettingsList.InfoRow(I18n.tr("settings.account.session", "Session"),
                () -> identity.online() ? I18n.tr("settings.account.online", "Microsoft account")
                        : I18n.tr("settings.account.offline", "Offline (development)")));
        list.add(new SettingsList.HeaderRow(I18n.tr("settings.account.medirian", "Medirian account")));
        final MedirianAccountService account = medirian.account();
        list.add(new SettingsList.InfoRow(I18n.tr("settings.account.status", "Status"), () -> accountStatus(account)));
        if (account.state() == MedirianAccountService.State.FAILED) {
            list.add(new SettingsList.ControlRow(I18n.tr("settings.account.failed", "Sign-in failed"), account.error(),
                    new Button(I18n.tr("settings.account.retry", "Retry"), Button.Style.SECONDARY, () -> {
                        account.retry();
                        buildTab();
                    }), 72));
        }
        list.add(new SettingsList.InfoRow(I18n.tr("settings.account.benefits", "Gives you"),
                () -> I18n.tr("settings.account.benefits.value", "capes seen by other players, profiles in the cloud")));
        list.add(new SettingsList.HeaderRow(I18n.tr("settings.account.client", "Client")));
        list.add(new SettingsList.InfoRow(I18n.tr("settings.account.version", "Medirian"),
                () -> BuildInfo.VERSION + " · Minecraft " + medirian.platform().minecraftVersion()));
        list.add(new SettingsList.InfoRow(I18n.tr("settings.account.launcher", "Launcher"),
                () -> medirian.launcherConnected() ? I18n.tr("settings.account.connected", "Connected")
                        : I18n.tr("settings.account.notConnected", "Not started from the launcher")));
        list.add(new SettingsList.InfoRow(I18n.tr("settings.account.home", "Data folder"), () -> medirian.home().root().getName()));
    }

    // ------------------------------------------------------------------ rendering

    @Override
    protected void render(Gfx g, float mx, float my, float delta) {
        lastRealWidth = (int) Math.round(g.width() * g.guiScale());
        lastRealHeight = (int) Math.round(g.height() * g.guiScale());
        Theme theme = Theme.current();
        renderBackdrop(g);
        g.fill(Math.round(px) + 3, Math.round(py) + 3, Math.round(px + pw) + 3, Math.round(py + ph) + 3, 0x50000000);
        Pixel.panel(g, px, py, pw, ph);
        Pixel.inset(g, px + 6, py + 30, NAV_W - 6, ph - 62, theme.panelDark);
        Pixel.icon(g, "settings", px + 8, py + 7, 1, 0xFFFFFFFF);
        Pixel.text(g, I18n.tr("ui.settings", "Settings"), px + 28, py + 11, theme.text);
        float y = py + 34;
        for (Tab t : Tab.values()) {
            boolean active = t == tab;
            boolean hovered = mx >= px + 10 && mx < px + NAV_W - 4 && my >= y && my < y + 20;
            Anim anim = navAnims.get(t);
            if (anim == null) {
                anim = new Anim(0f, 18f);
                navAnims.put(t, anim);
            }
            float h = anim.target(hovered ? 1f : 0f).get();
            if (active) {
                Pixel.frame(g, px + 10, y, NAV_W - 14, 20, theme.surfaceLight, 0xFF4C3870, theme.surfaceDark);
                g.fill(Math.round(px) + 11, Math.round(y) + 2, Math.round(px) + 13, Math.round(y) + 18, theme.pumpkin);
            } else if (h > 0.01f) {
                g.fill(Math.round(px) + 10, Math.round(y), Math.round(px + NAV_W) - 4, Math.round(y) + 20, Colors.fade(theme.surface, h));
            }
            Pixel.icon(g, tabIcon(t), px + 15, y + 2, 1, active || h > 0.5f ? 0xFFFFFFFF : 0xA0FFFFFF);
            g.text(UiDraw.ellipsize(g, tabName(t), (int) NAV_W - 40), px + 34, y + 6, active ? theme.text : Colors.lerp(theme.textDim, theme.text, h), false);
            y += 22;
        }
        backButton.render(g, mx, my);

        Pixel.icon(g, tabIcon(tab), px + NAV_W + 12, py + 7, 1, 0xFFFFFFFF);
        Pixel.text(g, tabName(tab), px + NAV_W + 32, py + 11, theme.pumpkinLight);
        Pixel.groove(g, px + NAV_W + 12, py + 28, pw - NAV_W - 20);
        list.render(g, mx, my);
    }

    // ------------------------------------------------------------------ input

    @Override
    protected boolean mouseClicked(float mx, float my, int button) {
        float y = py + 34;
        if (mx >= px + 10 && mx < px + NAV_W - 4) {
            for (Tab t : Tab.values()) {
                if (my >= y && my < y + 20) {
                    tab = t;
                    lastTab = t;
                    pendingDelete = null;
                    buildTab();
                    return true;
                }
                y += 22;
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
