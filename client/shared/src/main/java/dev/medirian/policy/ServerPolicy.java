package dev.medirian.policy;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.Charset;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A server's rules for Medirian, received on the {@link #CHANNEL} plugin channel as UTF-8 JSON:
 *
 * <pre>{ "version": 1, "disable": ["freelook", "zoom"], "message": "Freelook is not allowed here" }</pre>
 *
 * Module ids are Medirian's (see the mod menu / {@code config/profiles}). A new policy replaces the
 * previous one; an empty {@code disable} list lifts all restrictions.
 */
public final class ServerPolicy {

    /** Server → client: the policy. */
    public static final String CHANNEL = "medirian:policy";
    /** Client → server, after joining: {@code {"client":"Medirian","version":"…","minecraft":"…"}}. */
    public static final String HELLO_CHANNEL = "medirian:hello";
    /** Payloads larger than this are ignored. */
    public static final int MAX_SIZE = 32 * 1024;

    private static final Charset UTF8 = Charset.forName("UTF-8");

    public final Set<String> disabledModules;
    /** Shown to the player with the list of disabled modules; may be null. */
    public final String message;

    public ServerPolicy(Set<String> disabledModules, String message) {
        this.disabledModules = Collections.unmodifiableSet(disabledModules);
        this.message = message;
    }

    /** Parses a payload; returns null when it is not a valid policy. */
    public static ServerPolicy parse(byte[] payload) {
        if (payload == null || payload.length == 0 || payload.length > MAX_SIZE) {
            return null;
        }
        try {
            JsonElement root = new JsonParser().parse(new String(payload, UTF8));
            if (!root.isJsonObject()) {
                return null;
            }
            JsonObject json = root.getAsJsonObject();
            Set<String> disabled = new LinkedHashSet<String>();
            if (json.has("disable") && json.get("disable").isJsonArray()) {
                JsonArray array = json.getAsJsonArray("disable");
                for (JsonElement element : array) {
                    if (element.isJsonPrimitive()) {
                        disabled.add(element.getAsString().trim().toLowerCase());
                    }
                }
            }
            String message = json.has("message") && json.get("message").isJsonPrimitive() ? json.get("message").getAsString() : null;
            if (message != null && message.length() > 200) {
                message = message.substring(0, 200);
            }
            return new ServerPolicy(disabled, message);
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static byte[] hello(String clientVersion, String minecraftVersion) {
        JsonObject json = new JsonObject();
        json.addProperty("client", "Medirian");
        json.addProperty("version", clientVersion);
        json.addProperty("minecraft", minecraftVersion);
        return json.toString().getBytes(UTF8);
    }
}
