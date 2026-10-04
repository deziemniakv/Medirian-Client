package dev.meridian.cosmetics;

import com.google.gson.JsonObject;
import dev.meridian.config.JsonFiles;
import dev.meridian.core.Log;
import dev.meridian.core.MeridianHome;
import dev.meridian.services.MeridianServices;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Cosmetics: the catalogue and ownership (providers), renderers per type (registered by version
 * adapters) and the local player's {@link Loadout}, persisted in {@code config/cosmetics.json}
 * and, when signed in to Meridian services, shared with other players.
 *
 * <p>The cosmetics UI only exists in versions that registered a renderer.
 */
public final class CosmeticsManager {

    private final File file;
    private final List<CosmeticsProvider> providers = new CopyOnWriteArrayList<CosmeticsProvider>();
    private final Map<CosmeticType, CosmeticRenderer> renderers = new EnumMap<CosmeticType, CosmeticRenderer>(CosmeticType.class);
    private final Map<String, Cosmetic> byId = new HashMap<String, Cosmetic>();
    private final List<Cosmetic> ordered = new ArrayList<Cosmetic>();
    private volatile Loadout loadout = new Loadout();
    private RemoteCosmetics remote;
    private MeridianServices services;

    public CosmeticsManager(MeridianHome home) {
        this.file = new File(home.configDir(), "cosmetics.json");
    }

    public void load() {
        JsonObject json = JsonFiles.read(file);
        loadout = Loadout.fromJson(json == null ? null : json.getAsJsonObject("loadout"));
    }

    public void save() {
        JsonObject json = new JsonObject();
        json.addProperty("version", 1);
        json.add("loadout", loadout.toJson());
        try {
            JsonFiles.write(file, json);
        } catch (IOException e) {
            Log.error("Failed to save cosmetics", e);
        }
    }

    public void registerProvider(CosmeticsProvider provider) {
        providers.add(provider);
        for (Cosmetic cosmetic : provider.catalogue()) {
            if (!byId.containsKey(cosmetic.id())) {
                byId.put(cosmetic.id(), cosmetic);
                ordered.add(cosmetic);
            }
        }
    }

    /**
     * Connects to Meridian services: ownership and other players' loadouts come from there, and
     * the local loadout is uploaded after signing in and after every change.
     */
    public void connect(MeridianServices services) {
        this.services = services;
        this.remote = new RemoteCosmetics(services);
        registerProvider(remote);
        services.onSignedIn(() -> {
            remote.refreshOwned();
            remote.pushLoadout(loadout.copy());
        });
    }

    public void registerRenderer(CosmeticRenderer renderer) {
        renderers.put(renderer.type(), renderer);
    }

    public boolean canRender(CosmeticType type) {
        return renderers.containsKey(type);
    }

    /** True when this version renders at least one kind of cosmetic that the catalogue has. */
    public boolean available() {
        for (CosmeticType type : renderers.keySet()) {
            if (!catalogue(type).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** Cosmetics of one type, in catalogue order. */
    public List<Cosmetic> catalogue(CosmeticType type) {
        List<Cosmetic> list = new ArrayList<Cosmetic>();
        for (Cosmetic cosmetic : ordered) {
            if (cosmetic.type() == type) {
                list.add(cosmetic);
            }
        }
        return Collections.unmodifiableList(list);
    }

    public Cosmetic byId(String id) {
        return id == null ? null : byId.get(id);
    }

    /** Whether the local player may use the cosmetic. */
    public boolean owns(Cosmetic cosmetic) {
        for (CosmeticsProvider provider : providers) {
            if (provider.owns(cosmetic)) {
                return true;
            }
        }
        return false;
    }

    public Loadout loadout() {
        return loadout;
    }

    /** The cosmetic of {@code type} the local player wears (only if still owned), or null. */
    public Cosmetic equipped(CosmeticType type) {
        Cosmetic cosmetic = byId(loadout.equipped(type));
        return cosmetic != null && owns(cosmetic) ? cosmetic : null;
    }

    /** Equips a cosmetic the player owns. Returns false otherwise. */
    public boolean equip(Cosmetic cosmetic) {
        if (!owns(cosmetic)) {
            return false;
        }
        loadout.equip(cosmetic.type(), cosmetic.id());
        CosmeticRenderer renderer = renderers.get(cosmetic.type());
        if (renderer != null) {
            renderer.onEquipped(cosmetic);
        }
        changed();
        return true;
    }

    public void unequip(CosmeticType type) {
        loadout.equip(type, null);
        changed();
    }

    private void changed() {
        save();
        if (remote != null && services.state() == dev.meridian.account.MeridianAccountService.State.SIGNED_IN) {
            final Loadout copy = loadout.copy();
            services.submit(() -> remote.pushLoadout(copy));
        }
    }

    /**
     * Texture of the cape a player wears, or null. Called by the adapters for every rendered
     * player every frame, so it only does map lookups; other players' loadouts load in the
     * background and appear once known.
     *
     * @param local whether this is the player of this client
     */
    public String capeTexture(UUID player, boolean local) {
        Cosmetic cosmetic;
        if (local) {
            cosmetic = equipped(CosmeticType.CAPE);
        } else {
            Loadout other = null;
            for (CosmeticsProvider provider : providers) {
                other = provider.loadoutOf(player);
                if (other != null) {
                    break;
                }
            }
            cosmetic = other == null ? null : byId(other.equipped(CosmeticType.CAPE));
        }
        return cosmetic != null && cosmetic.type() == CosmeticType.CAPE ? cosmetic.asset() : null;
    }
}
