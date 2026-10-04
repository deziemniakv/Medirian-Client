package dev.meridian.services;

/**
 * Where Meridian services and the session server are. The launcher passes the services URL as
 * {@code -Dmeridian.api}; {@code MERIDIAN_API_URL} works for other launchers and development.
 * Without either, services are off and only local features work.
 */
public final class ServicesConfig {

    public static final String MOJANG_SESSION_SERVER = "https://sessionserver.mojang.com/session/minecraft";

    private final String apiUrl;
    private final String sessionServer;

    public ServicesConfig(String apiUrl, String sessionServer) {
        this.apiUrl = trimSlash(apiUrl);
        this.sessionServer = trimSlash(sessionServer == null || sessionServer.trim().isEmpty() ? MOJANG_SESSION_SERVER : sessionServer);
    }

    /** From system properties / environment. */
    public static ServicesConfig fromEnvironment() {
        String api = System.getProperty("meridian.api");
        if (api == null || api.trim().isEmpty()) {
            api = System.getenv("MERIDIAN_API_URL");
        }
        String sessionServer = System.getProperty("meridian.sessionServer");
        if (sessionServer == null || sessionServer.trim().isEmpty()) {
            // testing only (with an offline account and a fake session server), see backend/dev
            sessionServer = System.getenv("MERIDIAN_SESSION_SERVER");
        }
        return new ServicesConfig(api, sessionServer);
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
