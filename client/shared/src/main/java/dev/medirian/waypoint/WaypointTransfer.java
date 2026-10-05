package dev.medirian.waypoint;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.medirian.setting.ColorSetting;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Waypoints as text for sharing through the clipboard: export writes a JSON array (the entries of
 * {@code waypoints.json}); import also accepts plain lines such as {@code Base 120 64 -38}.
 */
public final class WaypointTransfer {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    /** "name x y z" with optional dimension id at the end; the name may contain spaces. */
    private static final Pattern LINE = Pattern.compile("^\\s*(.+?)\\s+(-?\\d+)[,\\s]+(-?\\d+)[,\\s]+(-?\\d+)(?:\\s+([a-z0-9_.-]+:[a-z0-9_/.-]+))?\\s*$");
    private static final int DEFAULT_COLOR = 0xFF9B55D6;

    private WaypointTransfer() {
    }

    public static String export(List<Waypoint> waypoints) {
        JsonArray array = new JsonArray();
        for (Waypoint waypoint : waypoints) {
            if (waypoint.death) {
                continue;
            }
            JsonObject json = new JsonObject();
            json.addProperty("name", waypoint.name);
            json.addProperty("x", waypoint.x);
            json.addProperty("y", waypoint.y);
            json.addProperty("z", waypoint.z);
            json.addProperty("dimension", waypoint.dimension);
            json.addProperty("color", ColorSetting.toHex(waypoint.color));
            array.add(json);
        }
        return GSON.toJson(array);
    }

    /**
     * Parses exported JSON or plain lines; unknown or broken entries are skipped.
     *
     * @param defaultDimension dimension of plain lines without one
     */
    public static List<Waypoint> parse(String text, String defaultDimension) {
        List<Waypoint> result = new ArrayList<Waypoint>();
        if (text == null) {
            return result;
        }
        String trimmed = text.trim();
        if (trimmed.startsWith("[") || trimmed.startsWith("{")) {
            try {
                JsonElement root = new JsonParser().parse(trimmed);
                JsonArray array = root.isJsonArray() ? root.getAsJsonArray() : new JsonArray();
                if (root.isJsonObject()) {
                    array.add(root);
                }
                for (JsonElement element : array) {
                    Waypoint waypoint = element.isJsonObject() ? fromJson(element.getAsJsonObject(), defaultDimension) : null;
                    if (waypoint != null) {
                        result.add(waypoint);
                    }
                }
                return result;
            } catch (RuntimeException e) {
                // not JSON after all: try the line format
            }
        }
        for (String line : trimmed.split("\\r?\\n")) {
            Matcher m = LINE.matcher(line);
            if (m.matches()) {
                String name = m.group(1).replaceAll("[,;:]+$", "").trim();
                name = name.length() > WaypointStore.MAX_NAME ? name.substring(0, WaypointStore.MAX_NAME) : name;
                result.add(new Waypoint(name, Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)),
                        Integer.parseInt(m.group(4)), m.group(5) != null ? m.group(5) : defaultDimension, DEFAULT_COLOR));
            }
        }
        return result;
    }

    private static Waypoint fromJson(JsonObject json, String defaultDimension) {
        try {
            String name = json.has("name") ? json.get("name").getAsString().trim() : "";
            if (name.isEmpty()) {
                return null;
            }
            Integer color = json.has("color") ? ColorSetting.parseHex(json.get("color").getAsString()) : null;
            return new Waypoint(name.length() > WaypointStore.MAX_NAME ? name.substring(0, WaypointStore.MAX_NAME) : name,
                    json.get("x").getAsInt(), json.get("y").getAsInt(), json.get("z").getAsInt(),
                    json.has("dimension") ? json.get("dimension").getAsString() : defaultDimension,
                    color == null ? DEFAULT_COLOR : color);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
