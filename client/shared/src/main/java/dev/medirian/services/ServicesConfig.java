package dev.medirian.services;

import dev.medirian.core.Log;

import java.net.URI;

/**
 * Where Medirian Services and the session server are. The launcher passes the services URL of its
 * build (MEDIRIAN_SERVICES_URL in the owner's .env) as {@code -Dmedirian.services}; the
 * {@code MEDIRIAN_SERVICES_URL} environment variable works for other launchers and development.
 * Without either, services are off and only local features work.
 *
 * <p>Services must be reached over HTTPS; plain HTTP is only accepted for this computer
 * (localhost / 127.0.0.1, local testing). Anything else is ignored with a warning.
 */
public final class ServicesConfig {

    public static final String MOJANG_SESSION_SERVER = "https://sessionserver.mojang.com/session/minecraft";

    private final String apiUrl;
    private final String sessionServer;

    public ServicesConfig(String apiUrl, String sessionServer) {
        this.apiUrl = secure("Medirian Services", trimSlash(apiUrl));
        String session = trimSlash(sessionServer);
        this.sessionServer = session.isEmpty() ? MOJANG_SESSION_SERVER : secure("session server", session);
    }

    /** From system properties / environment. */
    public static ServicesConfig fromEnvironment() {
        String api = System.getProperty("medirian.services");
        if (api == null || api.trim().isEmpty()) {
            api = System.getenv("MEDIRIAN_SERVICES_URL");
        }
        String sessionServer = System.getProperty("medirian.sessionServer");
        if (sessionServer == null || sessionServer.trim().isEmpty()) {
            // testing only (with an offline account and a fake session server), see backend/dev
            sessionServer = System.getenv("MEDIRIAN_SESSION_SERVER");
        }
        return new ServicesConfig(api, sessionServer);
    }

    /** {@code url} when it is HTTPS or HTTP to this computer, otherwise "" (off). */
    static String secure(String what, String url) {
        if (url.isEmpty()) {
            return url;
        }
        try {
            URI uri = new URI(url);
            String host = uri.getHost() == null ? "" : uri.getHost();
            if ("https".equalsIgnoreCase(uri.getScheme())) {
                return url;
            }
            if ("http".equalsIgnoreCase(uri.getScheme()) && (host.equals("localhost") || host.equals("127.0.0.1") || host.equals("[::1]") || host.equals("::1"))) {
                return url;
            }
        } catch (Exception ignored) {
            // reported below
        }
        Log.warn("Ignoring the {} address {}: HTTPS is required", what, url);
        return "";
    }

    public boolean enabled() {
        return !apiUrl.isEmpty();
    }

    public String apiUrl() {
        return apiUrl;
    }

    public String sessionServer() {
        return sessionServer;
    }

    /** A replacement session server is only used for testing with offline accounts. */
    public boolean customSessionServer() {
        return !MOJANG_SESSION_SERVER.equals(sessionServer);
    }

    private static String trimSlash(String url) {
        if (url == null) {
            return "";
        }
        String trimmed = url.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
