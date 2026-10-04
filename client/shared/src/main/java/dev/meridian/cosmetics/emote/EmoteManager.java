package dev.meridian.cosmetics.emote;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.meridian.account.MeridianAccountService;
import dev.meridian.core.Log;
import dev.meridian.cosmetics.Cosmetic;
import dev.meridian.cosmetics.CosmeticType;
import dev.meridian.cosmetics.CosmeticsManager;
import dev.meridian.net.Http;
import dev.meridian.platform.ClientActions;
import dev.meridian.platform.GameView;
import dev.meridian.services.MeridianServices;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;

/**
 * Emotes: the local player plays the equipped emote with the emote key (the camera turns to the
 * front view while it plays); other players' emotes come from Meridian services, which every
 * client asks about the players it sees once a second.
 */
public final class EmoteManager {

    static final long POLL_MS = 1000;

    private static final class Playing {
        final String emote;
        final long startedAt;

        Playing(String emote, long startedAt) {
            this.emote = emote;
            this.startedAt = startedAt;
        }
    }

    private final CosmeticsManager cosmetics;
    private final MeridianServices services;
    private final LongSupplier clock;
    private final Map<UUID, Playing> remote = new ConcurrentHashMap<UUID, Playing>();
    private final AtomicBoolean polling = new AtomicBoolean();
    private volatile Playing local;
    private int previousPerspective = -1;
    private long lastPoll;

    public EmoteManager(CosmeticsManager cosmetics, MeridianServices services) {
        this(cosmetics, services, System::currentTimeMillis);
    }

    EmoteManager(CosmeticsManager cosmetics, MeridianServices services, LongSupplier clock) {
        this.cosmetics = cosmetics;
        this.services = services;
        this.clock = clock;
    }

    /** Plays the equipped emote; false when none is equipped (or this version cannot show emotes). */
    public boolean playEquipped() {
        Cosmetic emote = cosmetics.canRender(CosmeticType.EMOTE) ? cosmetics.equipped(CosmeticType.EMOTE) : null;
        if (emote == null) {
            return false;
        }
        play(emote);
        return true;
    }

    public void play(final Cosmetic emote) {
        local = new Playing(emote.id(), clock.getAsLong());
        if (services != null && services.state() == MeridianAccountService.State.SIGNED_IN) {
            services.submit(() -> {
                JsonObject body = new JsonObject();
                body.addProperty("emote", emote.id());
                try {
                    Http.Response response = services.call("POST", "/v1/emotes/play", body);
                    if (!response.ok()) {
                        Log.warn("Could not share the emote: {}", response.error());
                    }
                } catch (IOException e) {
                    Log.warn("Could not share the emote: {}", e.toString());
                }
            });
        }
    }

    /** Whether the local player is playing an emote right now. */
    public boolean playingLocally() {
        Playing playing = local;
        return playing != null && clock.getAsLong() - playing.startedAt < Emotes.duration(playing.emote) * 1000;
    }

    /** Client tick: the camera for the local emote, and polling for the emotes of others. */
    public void tick(GameView game, ClientActions actions) {
        boolean playing = playingLocally();
        if (playing && previousPerspective < 0 && game.inWorld()) {
            previousPerspective = actions.perspective();
            if (previousPerspective == 0) {
                actions.setPerspective(2);
            }
        } else if (!playing && previousPerspective >= 0) {
            // back to first person only if the player did not change the view meanwhile
            if (previousPerspective == 0 && actions.perspective() == 2) {
                actions.setPerspective(0);
            }
            previousPerspective = -1;
            local = null;
        }
        if (services == null || !services.enabled() || !game.inWorld()) {
            remote.clear();
            return;
        }
        long now = clock.getAsLong();
        if (now - lastPoll < POLL_MS || polling.get()) {
            return;
        }
        final List<UUID> others = new ArrayList<UUID>();
        game.forEachPlayer((uuid, isLocal, x, y, z) -> {
            if (!isLocal) {
                others.add(uuid);
            }
        });
        remote.keySet().retainAll(others);
        if (others.isEmpty()) {
            return;
        }
        lastPoll = now;
        polling.set(true);
        services.submit(() -> poll(others));
    }

    /** Asks the services which of {@code players} are playing an emote (services thread). */
    void poll(List<UUID> players) {
        try {
            JsonArray array = new JsonArray();
            for (int i = 0; i < players.size() && i < 100; i++) {
                array.add(new JsonPrimitive(players.get(i).toString()));
            }
            JsonObject body = new JsonObject();
            body.add("players", array);
            Http.Response response = services.publicCall("POST", "/v1/emotes/active", body);
            if (!response.ok()) {
                return;
            }
            JsonElement emotes = response.json().get("emotes");
            JsonObject map = emotes != null && emotes.isJsonObject() ? emotes.getAsJsonObject() : new JsonObject();
            long now = clock.getAsLong();
            for (UUID player : players) {
                JsonElement entry = map.get(player.toString().replace("-", ""));
                if (entry == null || !entry.isJsonObject()) {
                    remote.remove(player);
                    continue;
                }
                JsonObject playing = entry.getAsJsonObject();
                String emote = playing.get("emote").getAsString();
                long startedAt = now - playing.get("elapsedMs").getAsLong();
                Playing known = remote.get(player);
                // keep the first start time we saw, so the animation does not jump every second
                if (known == null || !known.emote.equals(emote) || Math.abs(known.startedAt - startedAt) > 1500) {
                    remote.put(player, new Playing(emote, startedAt));
                }
            }
        } catch (IOException | RuntimeException e) {
            Log.warn("Could not load emotes: {}", e.toString());
        } finally {
            polling.set(false);
        }
    }

    /** Writes the current emote pose of a player into {@code out} (render thread); false when none plays. */
    public boolean pose(UUID player, boolean isLocal, EmotePose out) {
        Playing playing = isLocal ? local : remote.get(player);
        return playing != null && Emotes.pose(playing.emote, (clock.getAsLong() - playing.startedAt) / 1000.0, out);
    }
}
