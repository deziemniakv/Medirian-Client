package dev.meridian.cosmetics;

import com.google.gson.JsonObject;
import dev.meridian.config.JsonFiles;
import dev.meridian.core.Log;
import dev.meridian.core.MeridianHome;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Foundation of the cosmetics system: providers (catalogue/ownership), renderers per type
 * (implemented by version adapters) and the local player's {@link Loadout}, persisted in
 * {@code config/cosmetics.json}.
 *
 * <p>No cosmetic UI is exposed until at least one renderer is registered — see TODO.md.
 */
public final class CosmeticsManager {

    private final File file;
    private final List<CosmeticsProvider> providers = new ArrayList<CosmeticsProvider>();
    private final Map<CosmeticType, CosmeticRenderer> renderers = new EnumMap<CosmeticType, CosmeticRenderer>(CosmeticType.class);
    private Loadout loadout = new Loadout();

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
    }

    public void registerRenderer(CosmeticRenderer renderer) {
        renderers.put(renderer.type(), renderer);
    }

    public boolean canRender(CosmeticType type) {
        return renderers.containsKey(type);
    }

    /** True when any cosmetic type can be rendered in this version. */
    public boolean available() {
        return !renderers.isEmpty() && !providers.isEmpty();
    }

    public List<Cosmetic> catalogue() {
        List<Cosmetic> all = new ArrayList<Cosmetic>();
        for (CosmeticsProvider provider : providers) {
            all.addAll(provider.catalogue());
        }
        return Collections.unmodifiableList(all);
    }

    public Loadout loadout() {
        return loadout;
    }

    public void equip(Cosmetic cosmetic) {
        loadout.equip(cosmetic.type(), cosmetic.id());
        CosmeticRenderer renderer = renderers.get(cosmetic.type());
        if (renderer != null) {
            renderer.onEquipped(cosmetic);
        }
        save();
    }
}
