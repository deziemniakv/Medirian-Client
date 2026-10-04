package dev.meridian.services;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.meridian.config.ConfigManager;
import dev.meridian.net.Http;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Configuration profiles stored in the Meridian cloud: upload a local profile, download one
 * (replacing the local copy) or delete it. Network work runs on the services thread; callbacks
 * run on the client thread.
 */
public final class CloudProfiles {

    /** A profile in the cloud. */
    public static final class Entry {
        public final String name;
        public final long updatedAt;
        public final int size;

        Entry(String name, long updatedAt, int size) {
            this.name = name;
            this.updatedAt = updatedAt;
            this.size = size;
        }
    }

    /** Result of an operation: the value, or the error message when it failed. */
    public interface Callback<T> {
        void done(T value, String error);
    }

    private final MeridianServices services;
    private final ConfigManager config;
    private final Consumer<Runnable> clientThread;

    public CloudProfiles(MeridianServices services, ConfigManager config, Consumer<Runnable> clientThread) {
        this.services = services;
        this.config = config;
        this.clientThread = clientThread;
    }

    public void list(final Callback<List<Entry>> callback) {
        services.submit(() -> {
            try {
                Http.Response response = services.call("GET", "/v1/profiles", null);
                if (!response.ok()) {
                    reply(callback, null, response.error());
                    return;
                }
                List<Entry> entries = new ArrayList<Entry>();
                for (JsonElement element : response.json().getAsJsonArray("profiles")) {
                    JsonObject item = element.getAsJsonObject();
                    entries.add(new Entry(item.get("name").getAsString(), item.get("updatedAt").getAsLong(), item.get("size").getAsInt()));
                }
                Collections.sort(entries, (a, b) -> a.name.compareToIgnoreCase(b.name));
                reply(callback, entries, null);
            } catch (IOException | RuntimeException e) {
                reply(callback, null, e.toString());
            }
        });
    }

    /** Uploads a local profile (read now, on the client thread). */
    public void upload(final String name, final Callback<Long> callback) {
        final JsonObject data = config.exportProfile(name);
        if (data == null) {
            callback.done(null, "No such profile");
            return;
        }
        services.submit(() -> {
            JsonObject body = new JsonObject();
            body.add("data", data);
            try {
                Http.Response response = services.call("PUT", "/v1/profiles/" + encode(name), body);
                reply(callback, response.ok() ? response.json().get("updatedAt").getAsLong() : null, response.ok() ? null : response.error());
            } catch (IOException | RuntimeException e) {
                reply(callback, null, e.toString());
            }
        });
    }

    /** Downloads a cloud profile into the local profiles (applied when it is the active one). */
    public void download(final String name, final Callback<Boolean> callback) {
        services.submit(() -> {
            try {
                Http.Response response = services.call("GET", "/v1/profiles/" + encode(name), null);
                if (!response.ok()) {
                    reply(callback, null, response.error());
                    return;
                }
                final JsonObject data = response.json().getAsJsonObject("data");
                clientThread.accept(() -> {
                    boolean imported = config.importProfile(name, data);
                    callback.done(imported, imported ? null : "Invalid profile name");
                });
            } catch (IOException | RuntimeException e) {
                reply(callback, null, e.toString());
            }
        });
    }

    public void delete(final String name, final Callback<Boolean> callback) {
        services.submit(() -> {
            try {
                Http.Response response = services.call("DELETE", "/v1/profiles/" + encode(name), null);
                reply(callback, response.ok(), response.ok() ? null : response.error());
            } catch (IOException e) {
                reply(callback, null, e.toString());
            }
        });
    }

    private <T> void reply(final Callback<T> callback, final T value, final String error) {
        clientThread.accept(() -> callback.done(value, error));
    }

    /** Path segment encoding (spaces as %20, which the server decodes; URLEncoder would use '+'). */
    static String encode(String name) {
        try {
            return URLEncoder.encode(name, "UTF-8").replace("+", "%20");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}
