package dev.meridian.services;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.meridian.account.MeridianAccountService;
import dev.meridian.account.PlayerIdentity;
import dev.meridian.core.Log;
import dev.meridian.net.Http;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Client of Meridian services and the Meridian account.
 *
 * <p>Signing in: the backend hands out a random challenge ({@code serverId}); the client "joins"
 * it at Mojang's session server with the game's access token — exactly what the game does when
 * joining a Minecraft server — and the backend asks Mojang whether this player joined it
 * ({@code hasJoined}). The access token only ever goes to Mojang. The backend answers with a
 * session token for the other calls.
 *
 * <p>All network work runs on one background thread, in submission order.
 */
public final class MeridianServices implements MeridianAccountService {

    private final ServicesConfig config;
    private final PlayerIdentity identity;
    private final Supplier<String> accessToken;
    private final ScheduledExecutorService executor;
    private final List<Runnable> signInListeners = new CopyOnWriteArrayList<Runnable>();
    private volatile State state;
    private volatile String error;
    private volatile String displayName;
    private volatile String sessionToken;

    public MeridianServices(ServicesConfig config, PlayerIdentity identity, Supplier<String> accessToken) {
        this.config = config;
        this.identity = identity;
        this.accessToken = accessToken;
        this.executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "Meridian-Services");
            thread.setDaemon(true);
            return thread;
        });
        if (!config.enabled()) {
            state = State.UNAVAILABLE;
        } else if (!identity.online() && !config.customSessionServer()) {
            state = State.OFFLINE_ACCOUNT;
        } else {
            state = State.SIGNING_IN;
        }
    }

    /** Signs in in the background when services are configured and the account can be confirmed. */
    public void start() {
        if (state == State.SIGNING_IN) {
            submit(this::signIn);
        }
    }

    /** True when the services URL is configured (public calls work even without signing in). */
    public boolean enabled() {
        return config.enabled();
    }

    @Override
    public State state() {
        return state;
    }

    @Override
    public String displayName() {
        return displayName;
    }

    @Override
    public String error() {
        return error;
    }

    @Override
    public void retry() {
        if (state == State.FAILED) {
            state = State.SIGNING_IN;
            submit(this::signIn);
        }
    }

    /** Runs after every successful sign-in (on the services thread). */
    public void onSignedIn(Runnable listener) {
        signInListeners.add(listener);
    }

    /** Runs {@code task} on the services thread; failures are logged. */
    public Future<?> submit(Runnable task) {
        return executor.submit(guarded(task));
    }

    /** Runs {@code task} on the services thread after {@code delayMs}. */
    public void schedule(Runnable task, long delayMs) {
        executor.schedule(guarded(task), delayMs, TimeUnit.MILLISECONDS);
    }

    private static Runnable guarded(final Runnable task) {
        return () -> {
            try {
                task.run();
            } catch (RuntimeException e) {
                Log.error("Meridian services task failed", e);
            }
        };
    }

    /** A call that needs no account (call it on the services thread). */
    public Http.Response publicCall(String method, String path, JsonElement body) throws IOException {
        return Http.send(method, config.apiUrl() + path, body, null);
    }

    /**
     * A call as the signed-in player (call it on the services thread). An expired session is
     * renewed once; without an account the result is a 401 response.
     */
    public Http.Response call(String method, String path, JsonElement body) throws IOException {
        String token = sessionToken;
        if (token == null) {
            return new Http.Response(401, "{\"error\":\"Not signed in\"}");
        }
        Http.Response response = Http.send(method, config.apiUrl() + path, body, token);
        if (response.status == 401 && state == State.SIGNED_IN) {
            signIn();
            if (sessionToken != null) {
                response = Http.send(method, config.apiUrl() + path, body, sessionToken);
            }
        }
        return response;
    }

    private void signIn() {
        state = State.SIGNING_IN;
        sessionToken = null;
        try {
            Http.Response challenge = publicCall("POST", "/v1/auth/challenge", null);
            if (!challenge.ok()) {
                fail(challenge.error());
                return;
            }
            String serverId = challenge.json().get("serverId").getAsString();

            JsonObject join = new JsonObject();
            join.addProperty("accessToken", accessToken.get());
            join.addProperty("selectedProfile", identity.uuid().replace("-", ""));
            join.addProperty("serverId", serverId);
            Http.Response joined = Http.send("POST", config.sessionServer() + "/join", join, null);
            if (!joined.ok()) {
                fail("The Mojang session server refused the sign-in (" + joined.status + ")");
                return;
            }

            JsonObject request = new JsonObject();
            request.addProperty("name", identity.name());
            request.addProperty("serverId", serverId);
            Http.Response session = publicCall("POST", "/v1/auth/session", request);
            if (!session.ok()) {
                fail(session.error());
                return;
            }
            JsonObject json = session.json();
            sessionToken = json.get("token").getAsString();
            displayName = json.get("name").getAsString();
            error = null;
            state = State.SIGNED_IN;
            Log.info("Signed in to Meridian services as {}", displayName);
            for (Runnable listener : signInListeners) {
                listener.run();
            }
        } catch (IOException e) {
            fail("Meridian services are unreachable (" + e.getMessage() + ")");
        } catch (RuntimeException e) {
            fail("Unexpected answer from Meridian services");
            Log.error("Sign-in failed", e);
        }
    }

    private void fail(String message) {
        error = message;
        state = State.FAILED;
        Log.warn("Meridian services sign-in failed: {}", message);
    }

    public void shutdown() {
        executor.shutdownNow();
    }
}
