package dev.medirian.net;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.medirian.core.BuildInfo;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;

/** Minimal JSON-over-HTTP client (Java 8, no dependencies) for Medirian services. */
public final class Http {

    private static final Charset UTF_8 = Charset.forName("UTF-8");
    private static final int CONNECT_TIMEOUT_MS = 8000;
    private static final int READ_TIMEOUT_MS = 12000;

    private Http() {
    }

    public static final class Response {
        public final int status;
        public final String body;

        public Response(int status, String body) {
            this.status = status;
            this.body = body;
        }

        public boolean ok() {
            return status >= 200 && status < 300;
        }

        /** The body as a JSON object, or an empty object when it is empty or not an object. */
        public JsonObject json() {
            try {
                JsonElement element = new JsonParser().parse(body);
                return element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
            } catch (RuntimeException e) {
                return new JsonObject();
            }
        }

        /** The server's {@code error} message, or the status code. */
        public String error() {
            JsonObject json = json();
            return json.has("error") ? json.get("error").getAsString() : "HTTP " + status;
        }
    }

    /**
     * Sends a request. {@code body} is sent as JSON when not null, {@code bearer} as an
     * Authorization header when not null. Throws only for network errors.
     */
    public static Response send(String method, String url, JsonElement body, String bearer) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        try {
            connection.setRequestMethod(method);
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setUseCaches(false);
            connection.setRequestProperty("User-Agent", "Medirian/" + BuildInfo.VERSION);
            connection.setRequestProperty("Accept", "application/json");
            if (bearer != null) {
                connection.setRequestProperty("Authorization", "Bearer " + bearer);
            }
            if (body != null) {
                byte[] bytes = body.toString().getBytes(UTF_8);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                connection.setFixedLengthStreamingMode(bytes.length);
                OutputStream out = connection.getOutputStream();
                try {
                    out.write(bytes);
                } finally {
                    out.close();
                }
            }
            int status = connection.getResponseCode();
            InputStream in = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            return new Response(status, in == null ? "" : read(in));
        } finally {
            connection.disconnect();
        }
    }

    private static String read(InputStream in) throws IOException {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) > 0) {
                out.write(buffer, 0, n);
            }
            return new String(out.toByteArray(), UTF_8);
        } finally {
            in.close();
        }
    }
}
