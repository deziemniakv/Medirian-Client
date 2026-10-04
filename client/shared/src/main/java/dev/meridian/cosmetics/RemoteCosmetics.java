package dev.meridian.cosmetics;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.meridian.core.Log;
import dev.meridian.net.Http;
import dev.meridian.services.MeridianServices;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;

/**
 * Cosmetics from Meridian services: what the signed-in player owns, and the loadouts of other
 * players. Loadouts are looked up lazily — the first frame a player is rendered queues their
 * UUID; queued players are fetched together (up to 100 per request) and cached for 5 minutes.
 */
public final class RemoteCosmetics implements CosmeticsProvider {

    static final long TTL_MS = 5 * 60 * 1000L;
    static final long RETRY_MS = 60 * 1000L;
    static final long BATCH_DELAY_MS = 400;
    static final int BATCH = 100;
    private static final int CACHE_LIMIT = 4096;

    private static final class Entry {
        final Loadout loadout;
        final long expiresAt;

        Entry(Loadout loadout, long expiresAt) {
            this.loadout = loadout;
            this.expiresAt = expiresAt;
        }
    }

    private final MeridianServices services;
    private final LongSupplier clock;
    private final Map<UUID, Entry> cache = new ConcurrentHashMap<UUID, Entry>();
    private final Set<UUID> pending = Collections.newSetFromMap(new ConcurrentHashMap<UUID, Boolean>());
    private final AtomicBoolean flushScheduled = new AtomicBoolean();
    private volatile Set<String> owned = Collections.emptySet();

    public RemoteCosmetics(MeridianServices services) {
        this(services, System::currentTimeMillis);
    }

    RemoteCosmetics(MeridianServices services, LongSupplier clock) {
        this.services = services;
        this.clock = clock;
    }

    @Override
    public List<Cosmetic> catalogue() {
        return Collections.emptyList();
    }

    @Override
    public boolean owns(Cosmetic cosmetic) {
        return owned.contains(cosmetic.id());
    }

    @Override
    public Loadout loadoutOf(UUID player) {
        if (!services.enabled()) {
            return null;
        }
        Entry entry = cache.get(player);
        if (entry == null || clock.getAsLong() > entry.expiresAt) {
            request(player);
        }
        return entry == null ? null : entry.loadout;
    }

    private void request(UUID player) {
        if (pending.add(player) && flushScheduled.compareAndSet(false, true)) {
            services.schedule(this::flush, BATCH_DELAY_MS);
        }
    }

    /** Fetches queued players (services thread). */
    void flush() {
        flushScheduled.set(false);
        List<UUID> batch = new ArrayList<UUID>();
        for (Iterator<UUID> it = pending.iterator(); it.hasNext() && batch.size() < BATCH; ) {
            batch.add(it.next());
            it.remove();
        }
        if (batch.isEmpty()) {
            return;
        }
        JsonArray players = new JsonArray();
        for (UUID uuid : batch) {
            players.add(new JsonPrimitive(uuid.toString()));
        }
        JsonObject body = new JsonObject();
        body.add("players", players);
        long now = clock.getAsLong();
        try {
            Http.Response response = services.publicCall("POST", "/v1/cosmetics/loadouts", body);
            if (response.ok()) {
                JsonElement loadouts = response.json().get("loadouts");
                JsonObject map = loadouts != null && loadouts.isJsonObject() ? loadouts.getAsJsonObject() : new JsonObject();
                for (UUID uuid : batch) {
                    JsonElement loadout = map.get(uuid.toString().replace("-", ""));
                    Loadout parsed = loadout != null && loadout.isJsonObject() ? Loadout.fromJson(loadout.getAsJsonObject()) : new Loadout();
                    cache.put(uuid, new Entry(parsed, now + TTL_MS));
                }
            } else {
                Log.warn("Cosmetic loadouts unavailable: {}", response.error());
                retryLater(batch, now);
            }
        } catch (IOException e) {
            retryLater(batch, now);
        }
        trim(now);
        if (!pending.isEmpty() && flushScheduled.compareAndSet(false, true)) {
            services.schedule(this::flush, BATCH_DELAY_MS);
        }
    }

    private void retryLater(List<UUID> batch, long now) {
        for (UUID uuid : batch) {
            Entry old = cache.get(uuid);
            cache.put(uuid, new Entry(old == null ? null : old.loadout, now + RETRY_MS));
        }
    }

    private void trim(long now) {
        if (cache.size() > CACHE_LIMIT) {
            cache.values().removeIf(entry -> entry.expiresAt < now);
        }
    }

    /** Loads what the signed-in player owns (services thread). */
    void refreshOwned() {
        try {
            Http.Response response = services.call("GET", "/v1/cosmetics/owned", null);
            if (response.ok()) {
                Set<String> ids = new HashSet<String>();
                for (JsonElement id : response.json().getAsJsonArray("owned")) {
                    ids.add(id.getAsString());
                }
                owned = Collections.unmodifiableSet(ids);
            }
        } catch (IOException | RuntimeException e) {
            Log.warn("Could not load owned cosmetics: {}", e.toString());
        }
    }

    /** Stores the local player's loadout so others see it (services thread). */
    void pushLoadout(Loadout loadout) {
        JsonObject body = new JsonObject();
        body.add("loadout", loadout.toJson());
        try {
            Http.Response response = services.call("PUT", "/v1/cosmetics/loadout", body);
            if (!response.ok() && response.status != 401) {
                Log.warn("Could not save the cosmetic loadout: {}", response.error());
            }
        } catch (IOException e) {
            Log.warn("Could not save the cosmetic loadout: {}", e.toString());
        }
    }
}
