package dev.medirian.waypoint;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.medirian.config.JsonFiles;
import dev.medirian.core.Log;
import dev.medirian.setting.ColorSetting;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Waypoints of every world, keyed by {@link dev.medirian.platform.GameView#worldKey()}, stored in
 * {@code config/waypoints.json}. The same file is used by every Minecraft version.
 *
 * <pre>{ "schema": 1, "worlds": { "server:mc.example.net": [ { "name": "Base", "x": 10, "y": 64, "z": -3,
 *   "dimension": "minecraft:overworld", "color": "#FF9B55D6", "visible": true } ] } }</pre>
 */
public final class WaypointStore {

    private static final int SCHEMA = 1;
    public static final int MAX_NAME = 32;

    private final File file;
    private final Map<String, List<Waypoint>> worlds = new LinkedHashMap<String, List<Waypoint>>();
    private boolean loaded;

    public WaypointStore(File file) {
        this.file = file;
    }

    /** Waypoints of a world (live list, newest last). Never null. */
    public List<Waypoint> of(String worldKey) {
        load();
        List<Waypoint> list = worlds.get(worldKey);
        return list == null ? Collections.<Waypoint>emptyList() : Collections.unmodifiableList(list);
    }

    public Waypoint add(String worldKey, Waypoint waypoint) {
        load();
        List<Waypoint> list = worlds.get(worldKey);
        if (list == null) {
            list = new ArrayList<Waypoint>();
            worlds.put(worldKey, list);
        }
        if (waypoint.death) {
            for (int i = list.size() - 1; i >= 0; i--) {
                if (list.get(i).death) {
                    list.remove(i);
                }
            }
        }
        list.add(waypoint);
        changed();
        return waypoint;
    }

    public void remove(String worldKey, Waypoint waypoint) {
        load();
        List<Waypoint> list = worlds.get(worldKey);
        if (list != null && list.remove(waypoint)) {
            if (list.isEmpty()) {
                worlds.remove(worldKey);
            }
            changed();
        }
    }

    /** "Waypoint 3": the first free numbered name in a world. */
    public String nextName(String worldKey, String base) {
        for (int n = 1; ; n++) {
            String candidate = base + " " + n;
            boolean taken = false;
            for (Waypoint waypoint : of(worldKey)) {
                if (candidate.equalsIgnoreCase(waypoint.name)) {
                    taken = true;
                    break;
                }
            }
            if (!taken) {
                return candidate;
            }
        }
    }

    /** Saves after a waypoint was edited. The file is small, so it is written right away. */
    public void changed() {
        JsonObject root = new JsonObject();
        root.addProperty("schema", SCHEMA);
        JsonObject worldsJson = new JsonObject();
        for (Map.Entry<String, List<Waypoint>> entry : worlds.entrySet()) {
            JsonArray array = new JsonArray();
            for (Waypoint waypoint : entry.getValue()) {
                array.add(toJson(waypoint));
            }
            worldsJson.add(entry.getKey(), array);
        }
        root.add("worlds", worldsJson);
        try {
            JsonFiles.write(file, root);
        } catch (IOException e) {
            Log.error("Could not save waypoints to {}", file, e);
        }
    }

    private void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        JsonObject root = JsonFiles.read(file);
        if (root == null || !root.has("worlds") || !root.get("worlds").isJsonObject()) {
            return;
        }
        for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("worlds").entrySet()) {
            if (!entry.getValue().isJsonArray()) {
                continue;
            }
            List<Waypoint> list = new ArrayList<Waypoint>();
            for (JsonElement element : entry.getValue().getAsJsonArray()) {
                Waypoint waypoint = element.isJsonObject() ? fromJson(element.getAsJsonObject()) : null;
                if (waypoint != null) {
                    list.add(waypoint);
                }
            }
            if (!list.isEmpty()) {
                worlds.put(entry.getKey(), list);
            }
        }
    }

    private static JsonObject toJson(Waypoint waypoint) {
        JsonObject json = new JsonObject();
        json.addProperty("name", waypoint.name);
        json.addProperty("x", waypoint.x);
        json.addProperty("y", waypoint.y);
        json.addProperty("z", waypoint.z);
        json.addProperty("dimension", waypoint.dimension);
        json.addProperty("color", ColorSetting.toHex(waypoint.color));
        json.addProperty("visible", waypoint.visible);
        if (waypoint.death) {
            json.addProperty("death", true);
        }
        return json;
    }

    private static Waypoint fromJson(JsonObject json) {
        try {
            String name = json.has("name") ? json.get("name").getAsString() : "Waypoint";
            String dimension = json.has("dimension") ? json.get("dimension").getAsString() : "minecraft:overworld";
            Integer color = json.has("color") ? ColorSetting.parseHex(json.get("color").getAsString()) : null;
            Waypoint waypoint = new Waypoint(name.length() > MAX_NAME ? name.substring(0, MAX_NAME) : name,
                    json.get("x").getAsInt(), json.get("y").getAsInt(), json.get("z").getAsInt(), dimension,
                    color == null ? 0xFF9B55D6 : color);
            waypoint.visible = !json.has("visible") || json.get("visible").getAsBoolean();
            waypoint.death = json.has("death") && json.get("death").getAsBoolean();
            return waypoint;
        } catch (RuntimeException e) {
            Log.warn("Skipping invalid waypoint {}", json);
            return null;
        }
    }
}
