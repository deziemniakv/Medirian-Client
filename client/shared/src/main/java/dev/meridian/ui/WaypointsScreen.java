package dev.meridian.ui;

import dev.meridian.core.Meridian;
import dev.meridian.i18n.I18n;
import dev.meridian.input.Key;
import dev.meridian.module.impl.world.WaypointsModule;
import dev.meridian.platform.GameView;
import dev.meridian.platform.PlayerView;
import dev.meridian.render.Colors;
import dev.meridian.render.Gfx;
import dev.meridian.render.Theme;
import dev.meridian.render.UiDraw;
import dev.meridian.setting.BooleanSetting;
import dev.meridian.setting.ColorSetting;
import dev.meridian.setting.Setting;
import dev.meridian.setting.SettingsOwner;
import dev.meridian.setting.TextSetting;
import dev.meridian.ui.widget.Button;
import dev.meridian.ui.widget.ScrollState;
import dev.meridian.waypoint.Waypoint;
import dev.meridian.waypoint.WaypointStore;
import dev.meridian.waypoint.WaypointTransfer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Waypoints of the current world: list on the left, the selected waypoint's details on the right. */
public final class WaypointsScreen extends MeridianScreen {

    private static final float LIST_W = 200;
    private static final float ITEM_H = 26;

    private final WaypointsModule module;
    private final ScrollState scroll = new ScrollState();
    private float px;
    private float py;
    private float pw;
    private float ph;
    private float listTop;
    private float listBottom;
    private Button addButton;
    private Button exportButton;
    private Button importButton;
    private Button backButton;
    private SettingsList details;
    private Waypoint selected;
    private long confirmDeleteUntil;
    /** Scroll to the newest waypoint once the list has been measured again. */
    private boolean scrollToEnd;

    public WaypointsScreen(MeridianScreen parent) {
        super(parent);
        this.module = Meridian.get().modules().get(WaypointsModule.class);
    }

    private String world() {
        return Meridian.get().game().worldKey();
    }

    private List<Waypoint> waypoints() {
        String world = world();
        return world == null ? new ArrayList<Waypoint>() : module.store().of(world);
    }

    @Override
    protected void init() {
        pw = Math.min(600, width - 32);
        ph = Math.min(370, height - 32);
        px = (width - pw) / 2f;
        py = (height - ph) / 2f;
        listTop = py + 44;
        listBottom = py + ph - 70;
        addButton = new Button(I18n.tr("waypoints.addHere", "Add at my position"), Button.Style.PRIMARY, () -> {
            Waypoint added = module.addHere();
            if (added != null) {
                select(added);
                scrollToEnd = true;
            }
        }).bounds(px + 10, py + ph - 46, LIST_W - 20, 16);
        float half = (LIST_W - 24) / 2f;
        exportButton = new Button(I18n.tr("waypoints.export", "Export"), Button.Style.SECONDARY, this::exportToClipboard)
                .bounds(px + 10, py + ph - 66, half, 16);
        importButton = new Button(I18n.tr("waypoints.import", "Import"), Button.Style.SECONDARY, this::importFromClipboard)
                .bounds(px + 14 + half, py + ph - 66, half, 16);
        backButton = new Button(parent != null ? I18n.tr("ui.back", "Back") : I18n.tr("ui.done", "Done"),
                Button.Style.SECONDARY, this::close).bounds(px + 10, py + ph - 26, LIST_W - 20, 16);
        if (selected == null && !waypoints().isEmpty()) {
            selected = waypoints().get(waypoints().size() - 1);
        }
        buildDetails();
    }

    /** Copies this world's waypoints (without the death point) as JSON. */
    private void exportToClipboard() {
        List<Waypoint> list = waypoints();
        String text = WaypointTransfer.export(list);
        Meridian meridian = Meridian.get();
        meridian.platform().actions().setClipboard(text);
        int count = 0;
        for (Waypoint waypoint : list) {
            count += waypoint.death ? 0 : 1;
        }
        meridian.notifications().post(I18n.tr("notify.waypoint.exported", "Waypoints copied: {0}", count), null);
    }

    /** Adds waypoints from the clipboard (exported JSON or "name x y z" lines), skipping exact duplicates. */
    private void importFromClipboard() {
        Meridian meridian = Meridian.get();
        String world = world();
        PlayerView player = meridian.game().player();
        if (world == null || player == null) {
            return;
        }
        List<Waypoint> parsed = WaypointTransfer.parse(meridian.platform().actions().getClipboard(), player.dimensionId());
        int added = 0;
        Waypoint last = null;
        for (Waypoint candidate : parsed) {
            boolean duplicate = false;
            for (Waypoint existing : waypoints()) {
                if (existing.x == candidate.x && existing.y == candidate.y && existing.z == candidate.z
                        && existing.dimension.equals(candidate.dimension) && existing.name.equalsIgnoreCase(candidate.name)) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) {
                last = module.store().add(world, candidate);
                added++;
            }
        }
        if (added == 0) {
            meridian.notifications().post(I18n.tr("notify.waypoint.importNone", "No waypoints on the clipboard"), null);
            return;
        }
        meridian.notifications().post(I18n.tr("notify.waypoint.imported", "Waypoints imported: {0}", added), null,
                dev.meridian.notify.NotificationManager.Level.SUCCESS);
        select(last);
        scrollToEnd = true;
    }

    private void select(Waypoint waypoint) {
        selected = waypoint;
        confirmDeleteUntil = 0;
        buildDetails();
    }

    private void buildDetails() {
        details = new SettingsList(this);
        details.bounds(px + LIST_W + 14, py + 36, pw - LIST_W - 24, ph - 46);
        final Waypoint waypoint = selected;
        final String world = world();
        if (waypoint == null || world == null || !waypoints().contains(waypoint)) {
            selected = null;
            return;
        }
        final WaypointStore store = module.store();
        details.addSettings(new Editor(waypoint, store).settings());
        details.add(new SettingsList.InfoRow(I18n.tr("waypoints.position", "Position"), () -> WaypointsModule.coordinates(waypoint)));
        details.add(new SettingsList.InfoRow(I18n.tr("waypoints.dimension", "Dimension"), () -> dimensionName(waypoint.dimension)));
        details.add(new SettingsList.InfoRow(I18n.tr("waypoints.distance", "Distance"), () -> distance(waypoint)));
        details.add(new SettingsList.ControlRow(I18n.tr("waypoints.move", "Move to my position"), null,
                new Button(I18n.tr("waypoints.moveButton", "Move"), Button.Style.SECONDARY, () -> {
                    PlayerView player = Meridian.get().game().player();
                    if (player != null) {
                        waypoint.x = (int) Math.floor(player.x());
                        waypoint.y = (int) Math.floor(player.y());
                        waypoint.z = (int) Math.floor(player.z());
                        waypoint.dimension = player.dimensionId();
                        store.changed();
                    }
                }), 72));
        details.add(new SettingsList.ControlRow(I18n.tr("waypoints.delete", "Delete waypoint"), null,
                new Button(() -> System.currentTimeMillis() < confirmDeleteUntil ? I18n.tr("ui.confirm", "Confirm")
                        : I18n.tr("ui.delete", "Delete"), Button.Style.DANGER, () -> {
                    if (System.currentTimeMillis() < confirmDeleteUntil) {
                        store.remove(world, waypoint);
                        List<Waypoint> rest = waypoints();
                        select(rest.isEmpty() ? null : rest.get(rest.size() - 1));
                    } else {
                        confirmDeleteUntil = System.currentTimeMillis() + 3000;
                    }
                }), 72));
    }

    private static String distance(Waypoint waypoint) {
        PlayerView player = Meridian.get().game().player();
        if (player == null) {
            return "-";
        }
        if (!waypoint.dimension.equals(player.dimensionId())) {
            return I18n.tr("waypoints.otherDimension", "other dimension");
        }
        return WaypointsModule.formatDistance(Math.sqrt(waypoint.distanceSq(player.x(), player.y(), player.z())));
    }

    static String dimensionName(String id) {
        if ("minecraft:overworld".equals(id)) {
            return I18n.tr("dimension.overworld", "Overworld");
        }
        if ("minecraft:the_nether".equals(id)) {
            return I18n.tr("dimension.nether", "Nether");
        }
        if ("minecraft:the_end".equals(id)) {
            return I18n.tr("dimension.end", "The End");
        }
        return id;
    }

    /** The selected waypoint's editable fields as settings, so {@link SettingsList} builds the controls. */
    private static final class Editor implements SettingsOwner {
        private final Waypoint waypoint;
        private final WaypointStore store;
        private final TextSetting name;
        private final ColorSetting color;
        private final BooleanSetting visible;

        Editor(Waypoint waypoint, WaypointStore store) {
            this.waypoint = waypoint;
            this.store = store;
            name = new TextSetting("name", "Name", waypoint.name, WaypointStore.MAX_NAME);
            color = new ColorSetting("color", "Color", waypoint.color, false);
            visible = new BooleanSetting("visible", "Show marker", waypoint.visible);
            for (Setting<?> setting : settings()) {
                setting.attach(this);
            }
        }

        @Override
        public String settingsNamespace() {
            return "waypoint";
        }

        @Override
        public List<Setting<?>> settings() {
            return Arrays.<Setting<?>>asList(name, color, visible);
        }

        @Override
        public void onSettingChanged(Setting<?> setting) {
            String newName = name.get().trim();
            if (!newName.isEmpty()) {
                waypoint.name = newName;
            }
            waypoint.color = color.argb() | 0xFF000000;
            waypoint.visible = visible.on();
            store.changed();
        }
    }

    // ------------------------------------------------------------------ rendering

    @Override
    protected void render(Gfx g, float mx, float my, float delta) {
        Theme theme = Theme.current();
        renderBackdrop(g);
        UiDraw.shadow(g, px, py, pw, ph, 8, 4);
        UiDraw.roundRectBordered(g, px, py, pw, ph, 8, theme.panel, theme.border);
        UiDraw.roundRect(g, px + 1, py + 1, LIST_W, ph - 2, 7, theme.surface);
        g.fill((int) (px + LIST_W), (int) py + 1, (int) (px + LIST_W) + 1, (int) (py + ph) - 1, theme.border);

        g.text("§l" + I18n.tr("waypoints.title", "Waypoints"), px + 12, py + 12, theme.text, false);
        String world = world();
        String worldLabel = world == null ? I18n.tr("waypoints.noWorld", "Not in a world")
                : world.substring(world.indexOf(':') + 1);
        g.text(UiDraw.ellipsize(g, worldLabel, (int) LIST_W - 24), px + 12, py + 26, theme.textMuted, false);
        renderList(g, mx, my, theme);
        addButton.enabled(world != null).render(g, mx, my);
        exportButton.enabled(world != null && !waypoints().isEmpty()).render(g, mx, my);
        importButton.enabled(world != null).render(g, mx, my);
        backButton.render(g, mx, my);

        float dx = px + LIST_W + 14;
        if (selected == null) {
            String hint = world == null ? I18n.tr("waypoints.hint.noWorld", "Join a world to manage its waypoints.")
                    : I18n.tr("waypoints.hint.empty", "Select a waypoint, or add one at your position.");
            g.text(hint, dx, py + 40, theme.textDim, false);
            return;
        }
        g.text("§l" + UiDraw.ellipsize(g, selected.name, (int) (pw - LIST_W - 40)), dx, py + 14, theme.text, false);
        g.fill((int) dx, (int) py + 28, (int) (px + pw) - 10, (int) py + 29, theme.border);
        details.render(g, mx, my);
    }

    private void renderList(Gfx g, float mx, float my, Theme theme) {
        List<Waypoint> list = waypoints();
        float viewport = listBottom - listTop;
        scroll.setBounds(list.size() * ITEM_H, viewport);
        if (scrollToEnd) {
            scrollToEnd = false;
            scroll.scroll(-list.size());
        }
        if (list.isEmpty()) {
            if (world() != null) {
                g.text(I18n.tr("waypoints.none", "No waypoints yet"), px + 12, listTop + 6, theme.textDim, false);
            }
            return;
        }
        PlayerView player = Meridian.get().game().player();
        g.enableScissor((int) px, (int) listTop, (int) (px + LIST_W), (int) listBottom);
        float y = listTop - scroll.offset();
        for (Waypoint waypoint : list) {
            if (y + ITEM_H > listTop && y < listBottom) {
                boolean hovered = mx >= px + 6 && mx < px + LIST_W - 6 && my >= Math.max(y, listTop) && my < Math.min(y + ITEM_H, listBottom);
                if (waypoint == selected) {
                    UiDraw.roundRect(g, px + 6, y + 1, LIST_W - 12, ITEM_H - 2, 3, theme.accentSoft);
                } else if (hovered) {
                    UiDraw.roundRect(g, px + 6, y + 1, LIST_W - 12, ITEM_H - 2, 3, theme.elevated);
                }
                int color = waypoint.visible ? waypoint.color : Colors.fade(waypoint.color, 0.35f);
                marker(g, px + 16, y + ITEM_H / 2f, color);
                int textColor = waypoint.visible ? theme.text : theme.textMuted;
                g.text(UiDraw.ellipsize(g, waypoint.name, (int) LIST_W - 44), px + 26, y + 5, textColor, false);
                String sub = WaypointsModule.coordinates(waypoint);
                if (player != null && !waypoint.dimension.equals(player.dimensionId())) {
                    sub += " · " + dimensionName(waypoint.dimension);
                } else if (player != null) {
                    sub += " · " + WaypointsModule.formatDistance(Math.sqrt(waypoint.distanceSq(player.x(), player.y(), player.z())));
                }
                g.push();
                g.translate(px + 26, y + 15);
                g.scale(0.8f, 0.8f);
                g.text(UiDraw.ellipsize(g, sub, (int) ((LIST_W - 44) / 0.8f)), 0, 0, theme.textMuted, false);
                g.pop();
            }
            y += ITEM_H;
        }
        g.disableScissor();
        scroll.renderBar(g, px + LIST_W - 4, listTop);
    }

    private static void marker(Gfx g, float cx, float cy, int argb) {
        int x = Math.round(cx);
        int y = Math.round(cy);
        for (int i = -3; i <= 3; i++) {
            int half = 3 - Math.abs(i);
            g.fill(x - half, y + i, x + half + 1, y + i + 1, argb);
        }
    }

    // ------------------------------------------------------------------ input

    @Override
    protected boolean mouseClicked(float mx, float my, int button) {
        if (addButton.mouseClicked(mx, my, button) || exportButton.mouseClicked(mx, my, button)
                || importButton.mouseClicked(mx, my, button) || backButton.mouseClicked(mx, my, button)) {
            return true;
        }
        if (mx >= px + 6 && mx < px + LIST_W - 6 && my >= listTop && my < listBottom) {
            int index = (int) ((my - listTop + scroll.offset()) / ITEM_H);
            List<Waypoint> list = waypoints();
            if (index >= 0 && index < list.size()) {
                select(list.get(index));
            }
            return true;
        }
        return details.mouseClicked(mx, my, button);
    }

    @Override
    protected boolean mouseReleased(float mx, float my, int button) {
        details.mouseReleased(mx, my, button);
        return true;
    }

    @Override
    protected boolean mouseDragged(float mx, float my, int button) {
        details.mouseDragged(mx, my);
        return true;
    }

    @Override
    protected boolean mouseScrolled(float mx, float my, double amount) {
        if (mx >= px && mx < px + LIST_W && my >= listTop && my < listBottom) {
            scroll.scroll(amount);
            return true;
        }
        return details.mouseScrolled(mx, my, amount);
    }

    @Override
    protected boolean keyPressed(Key key, boolean ctrl, boolean shift) {
        return details.keyPressed(key, ctrl, shift, mouseX(), mouseY());
    }
}
