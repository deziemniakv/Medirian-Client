package dev.meridian.module.impl.world;

import dev.meridian.core.Meridian;
import dev.meridian.event.Events;
import dev.meridian.i18n.I18n;
import dev.meridian.input.Key;
import dev.meridian.module.Category;
import dev.meridian.module.Module;
import dev.meridian.notify.NotificationManager;
import dev.meridian.platform.CameraView;
import dev.meridian.platform.Capability;
import dev.meridian.platform.GameView;
import dev.meridian.platform.PlayerView;
import dev.meridian.render.Gfx;
import dev.meridian.render.Projection;
import dev.meridian.setting.ActionSetting;
import dev.meridian.setting.BooleanSetting;
import dev.meridian.setting.KeySetting;
import dev.meridian.setting.NumberSetting;
import dev.meridian.ui.WaypointsScreen;
import dev.meridian.waypoint.Waypoint;
import dev.meridian.waypoint.WaypointStore;

import java.io.File;
import java.util.List;

/**
 * Waypoints: named positions per world and dimension, shown as markers with their distance at
 * their position on screen (also through walls). Managed in {@link WaypointsScreen}; added with a
 * key or automatically where the player died.
 */
public final class WaypointsModule extends Module {

    /** Colours given to new waypoints in turn. */
    private static final int[] PALETTE = {
            0xFF9B55D6, 0xFFF08A24, 0xFF2BB5A0, 0xFF4C8DFF, 0xFFE0559B, 0xFFE8C547, 0xFF5CC46B
    };
    private static final int DEATH_COLOR = 0xFFE5484D;

    private final BooleanSetting showDistance;
    private final NumberSetting scale;
    private final BooleanSetting deathPoints;
    private final float[] projected = new float[3];
    private WaypointStore store;

    public WaypointsModule() {
        super("waypoints", "Waypoints", Category.WORLD, "Mark places and see where they are, even through walls.");
        requires(Capability.WAYPOINTS);
        add(new ActionSetting("manage", "Waypoints", "Manage", () -> {
            Meridian meridian = Meridian.get();
            meridian.platform().openScreen(new WaypointsScreen(meridian.platform().currentScreen()));
        }));
        add(new KeySetting("addKey", "Add waypoint key", Key.NONE).onPress(this::addHere));
        showDistance = add(new BooleanSetting("showDistance", "Show distance", true));
        scale = add(new NumberSetting("scale", "Marker size", 1, 0.5, 2, 0.1).unit("x"));
        deathPoints = add(new BooleanSetting("deathPoints", "Death waypoint", true)
                .description("Marks the place where you died last."));
        on(Events.RenderHud.class, e -> render(e.gfx));
        on(Events.PlayerDeath.class, e -> onDeath());
    }

    /** Waypoints of every world ({@code config/waypoints.json} in Meridian's data folder). */
    public WaypointStore store() {
        if (store == null) {
            store = new WaypointStore(new File(Meridian.get().home().configDir(), "waypoints.json"));
        }
        return store;
    }

    /** For tests: use another store file. */
    public void useStore(WaypointStore other) {
        store = other;
    }

    /** Adds a waypoint at the player's position; returns null when not in a world. */
    public Waypoint addHere() {
        GameView game = Meridian.get().game();
        PlayerView player = game.player();
        String world = game.worldKey();
        if (player == null || world == null) {
            return null;
        }
        WaypointStore waypoints = store();
        String name = waypoints.nextName(world, I18n.tr("waypoints.defaultName", "Waypoint"));
        Waypoint waypoint = waypoints.add(world, new Waypoint(name, floor(player.x()), floor(player.y()), floor(player.z()),
                player.dimensionId(), PALETTE[waypoints.of(world).size() % PALETTE.length]));
        Meridian.get().notifications().post(I18n.tr("notify.waypoint.added", "Waypoint added"),
                name + " · " + coordinates(waypoint), NotificationManager.Level.SUCCESS);
        return waypoint;
    }

    private void onDeath() {
        GameView game = Meridian.get().game();
        PlayerView player = game.player();
        String world = game.worldKey();
        if (!deathPoints.on() || player == null || world == null) {
            return;
        }
        Waypoint waypoint = new Waypoint(I18n.tr("waypoints.death", "Death"), floor(player.x()), floor(player.y()),
                floor(player.z()), player.dimensionId(), DEATH_COLOR);
        waypoint.death = true;
        store().add(world, waypoint);
        Meridian.get().notifications().post(I18n.tr("notify.waypoint.death", "Death point saved"), coordinates(waypoint));
    }

    private void render(Gfx g) {
        GameView game = Meridian.get().game();
        String world = game.worldKey();
        PlayerView player = game.player();
        CameraView camera = game.camera();
        if (world == null || player == null || camera == null) {
            return;
        }
        List<Waypoint> waypoints = store().of(world);
        if (waypoints.isEmpty()) {
            return;
        }
        String dimension = player.dimensionId();
        float width = g.width();
        float height = g.height();
        for (Waypoint waypoint : waypoints) {
            if (!waypoint.visible || !waypoint.dimension.equals(dimension)
                    || !Projection.project(camera, waypoint.x + 0.5, waypoint.y + 1.0, waypoint.z + 0.5, width, height, projected)) {
                continue;
            }
            float sx = projected[0];
            float sy = projected[1];
            if (sx < -40 || sy < -40 || sx > width + 40 || sy > height + 40) {
                continue;
            }
            double distance = Math.sqrt(waypoint.distanceSq(player.x(), player.y(), player.z()));
            drawMarker(g, sx, sy, waypoint, distance);
        }
    }

    private void drawMarker(Gfx g, float sx, float sy, Waypoint waypoint, double distance) {
        g.push();
        g.translate(Math.round(sx), Math.round(sy));
        float s = scale.floatValue();
        g.scale(s, s);
        diamond(g, 5, 0xC0101014);
        diamond(g, 4, waypoint.color);
        String name = waypoint.name;
        int nameWidth = g.textWidth(name);
        int top = -8 - g.fontHeight();
        g.fill(-nameWidth / 2 - 2, top - 2, nameWidth - nameWidth / 2 + 2, top + g.fontHeight(), 0x90000000);
        g.text(name, -nameWidth / 2f, top, 0xFFFFFFFF, true);
        if (showDistance.on()) {
            String text = formatDistance(distance);
            int textWidth = g.textWidth(text);
            g.fill(-textWidth / 2 - 2, 7, textWidth - textWidth / 2 + 2, 8 + g.fontHeight(), 0x70000000);
            g.text(text, -textWidth / 2f, 8, 0xFFD0D0D8, true);
        }
        g.pop();
    }

    /** A filled diamond of radius {@code r} centred on the origin, drawn as horizontal spans. */
    private static void diamond(Gfx g, int r, int argb) {
        for (int i = -r; i <= r; i++) {
            int half = r - Math.abs(i);
            g.fill(-half, i, half + 1, i + 1, argb);
        }
    }

    public static String formatDistance(double blocks) {
        return blocks >= 1000 ? String.format(java.util.Locale.ROOT, "%.1f km", blocks / 1000) : Math.round(blocks) + " m";
    }

    public static String coordinates(Waypoint waypoint) {
        return waypoint.x + " " + waypoint.y + " " + waypoint.z;
    }

    private static int floor(double value) {
        return (int) Math.floor(value);
    }
}
