package dev.medirian.cosmetics;

import com.google.gson.JsonObject;
import dev.medirian.config.JsonFiles;
import dev.medirian.core.Log;
import dev.medirian.core.MedirianHome;
import dev.medirian.services.MedirianServices;

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
 * and, when signed in to Medirian services, shared with other players.
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
    private MedirianServices services;

    public CosmeticsManager(MedirianHome home) {
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
     * Connects to Medirian services: ownership and other players' loadouts come from there, and
     * the local loadout is uploaded after signing in and after every change.
     */
    public void connect(MedirianServices services) {
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
        if (remote != null && services.state() == dev.medirian.account.MedirianAccountService.State.SIGNED_IN) {
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
        Cosmetic cosmetic = worn(player, local, CosmeticType.CAPE);
        return cosmetic == null ? null : cosmetic.asset();
    }

    /**
     * The cosmetic of {@code type} a player wears, or null. Same rules as {@link #capeTexture}:
     * the local player's own loadout (only owned cosmetics), other players' loadouts from the
     * services once known. Only types this version renders are returned.
     */
    public Cosmetic worn(UUID player, boolean local, CosmeticType type) {
        if (!renderers.containsKey(type)) {
            return null;
        }
        Cosmetic cosmetic;
        if (local) {
            cosmetic = equipped(type);
        } else {
            Loadout other = null;
            for (CosmeticsProvider provider : providers) {
                other = provider.loadoutOf(player);
                if (other != null) {
                    break;
                }
            }
            cosmetic = other == null ? null : byId(other.equipped(type));
        }
        return cosmetic != null && cosmetic.type() == type ? cosmetic : null;
    }
}
