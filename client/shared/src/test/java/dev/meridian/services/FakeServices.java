package dev.meridian.services;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A fake of Meridian services and of Mojang's session server in one HTTP server, for tests.
 * Records every request so tests can check what was sent where.
 */
public final class FakeServices implements AutoCloseable {

    private static final Charset UTF_8 = Charset.forName("UTF-8");

    /** One recorded request. */
    public static final class Request {
        public final String method;
        public final String path;
        public final String body;
        public final String authorization;

        Request(String method, String path, String body, String authorization) {
            this.method = method;
            this.path = path;
            this.body = body;
            this.authorization = authorization;
        }
    }

    public final List<Request> requests = Collections.synchronizedList(new ArrayList<Request>());
    /** Status answered to /session/minecraft/join (204 = accepted, 403 = bad token). */
    public volatile int joinStatus = 204;
    /** Loadouts by UUID without dashes. */
    public final Map<String, JsonObject> loadouts = Collections.synchronizedMap(new HashMap<String, JsonObject>());
    /** Emotes being played, by UUID without dashes. */
    public final Map<String, String> emotes = Collections.synchronizedMap(new HashMap<String, String>());
    public final Map<String, JsonObject> profiles = Collections.synchronizedMap(new HashMap<String, JsonObject>());
    public volatile String validToken;
    private final Map<String, String> joined = Collections.synchronizedMap(new HashMap<String, String>());
    private final HttpServer server;
    private int tokens;

    public FakeServices() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
    }

    public String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public ServicesConfig config() {
        return new ServicesConfig(url(), url() + "/session/minecraft");
    }

    /** Requests to a path. */
    public List<Request> to(String path) {
        List<Request> list = new ArrayList<Request>();
        synchronized (requests) {
            for (Request request : requests) {
                if (request.path.equals(path)) {
                    list.add(request);
                }
            }
        }
        return list;
    }

    private void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getRawPath();
        String body = read(exchange.getRequestBody());
        String auth = exchange.getRequestHeaders().getFirst("Authorization");
        requests.add(new Request(method, path, body, auth));
        JsonObject json = body.isEmpty() ? new JsonObject() : new JsonParser().parse(body).getAsJsonObject();
        boolean authorized = auth != null && validToken != null && auth.equals("Bearer " + validToken);

        if (path.equals("/v1/auth/challenge")) {
            reply(exchange, 200, "{\"serverId\":\"" + Integer.toHexString(requests.size()) + "abc\"}");
        } else if (path.equals("/session/minecraft/join")) {
            if (joinStatus == 204) {
                joined.put(json.get("serverId").getAsString(), json.get("selectedProfile").getAsString());
            }
            reply(exchange, joinStatus, joinStatus == 204 ? null : "{\"error\":\"ForbiddenOperationException\"}");
        } else if (path.equals("/v1/auth/session")) {
            String uuid = joined.remove(json.get("serverId").getAsString());
            if (uuid == null) {
                reply(exchange, 401, "{\"error\":\"Mojang did not confirm the session\"}");
                return;
            }
            validToken = "token-" + (++tokens);
            reply(exchange, 200, "{\"token\":\"" + validToken + "\",\"uuid\":\"" + uuid + "\",\"name\":\"" + json.get("name").getAsString() + "\"}");
        } else if (path.equals("/v1/cosmetics/loadouts")) {
            JsonObject result = new JsonObject();
            for (JsonElement player : json.getAsJsonArray("players")) {
                String uuid = player.getAsString().replace("-", "");
                if (loadouts.containsKey(uuid)) {
                    result.add(uuid, loadouts.get(uuid));
                }
            }
            JsonObject wrapper = new JsonObject();
            wrapper.add("loadouts", result);
            reply(exchange, 200, wrapper.toString());
        } else if (path.equals("/v1/emotes/active")) {
            JsonObject result = new JsonObject();
            for (JsonElement player : json.getAsJsonArray("players")) {
                String uuid = player.getAsString().replace("-", "");
                if (emotes.containsKey(uuid)) {
                    JsonObject playing = new JsonObject();
                    playing.addProperty("emote", emotes.get(uuid));
                    playing.addProperty("elapsedMs", 500);
                    result.add(uuid, playing);
                }
            }
            JsonObject wrapper = new JsonObject();
            wrapper.add("emotes", result);
            reply(exchange, 200, wrapper.toString());
        } else if (!authorized) {
            reply(exchange, 401, "{\"error\":\"Sign in required\"}");
        } else if (path.equals("/v1/cosmetics/owned")) {
            reply(exchange, 200, "{\"owned\":[\"cape_moonlit\",\"cape_founder\"]}");
        } else if (path.equals("/v1/emotes/play")) {
            reply(exchange, 200, json.toString());
        } else if (path.equals("/v1/cosmetics/loadout")) {
            reply(exchange, 200, json.toString());
        } else if (path.equals("/v1/profiles")) {
            StringBuilder list = new StringBuilder("{\"profiles\":[");
            synchronized (profiles) {
                int i = 0;
                for (String name : profiles.keySet()) {
                    list.append(i++ > 0 ? "," : "").append("{\"name\":\"").append(name).append("\",\"updatedAt\":1000,\"size\":10}");
                }
            }
            reply(exchange, 200, list.append("]}").toString());
        } else if (path.startsWith("/v1/profiles/")) {
            String name = java.net.URLDecoder.decode(path.substring("/v1/profiles/".length()).replace("+", "%2B"), "UTF-8");
            if (method.equals("PUT")) {
                profiles.put(name, json.getAsJsonObject("data"));
                reply(exchange, 200, "{\"name\":\"" + name + "\",\"updatedAt\":2000}");
            } else if (profiles.containsKey(name)) {
                reply(exchange, 200, "{\"name\":\"" + name + "\",\"updatedAt\":1000,\"data\":" + profiles.get(name) + "}");
            } else {
                reply(exchange, 404, "{\"error\":\"No such profile\"}");
            }
        } else {
            reply(exchange, 404, "{\"error\":\"Not found\"}");
        }
    }

    private static void reply(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body == null ? new byte[0] : body.getBytes(UTF_8);
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            OutputStream out = exchange.getResponseBody();
            out.write(bytes);
            out.close();
        }
        exchange.close();
    }

    private static String read(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int n;
        while ((n = in.read(buffer)) > 0) {
            out.write(buffer, 0, n);
        }
        return new String(out.toByteArray(), UTF_8);
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
