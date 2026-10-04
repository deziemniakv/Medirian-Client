package dev.meridian.cosmetics;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.meridian.core.Log;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * The cosmetics that ship with the client ({@code /meridian/cosmetics/catalogue.json}, the same
 * catalogue the services use). Free ones are usable by everyone without an account.
 */
public final class BundledCosmetics implements CosmeticsProvider {

    private final List<Cosmetic> catalogue;

    public BundledCosmetics() {
        this(load());
    }

    BundledCosmetics(List<Cosmetic> catalogue) {
        this.catalogue = Collections.unmodifiableList(catalogue);
    }

    @Override
    public List<Cosmetic> catalogue() {
        return catalogue;
    }

    @Override
    public boolean owns(Cosmetic cosmetic) {
        return cosmetic.access() == Cosmetic.Access.FREE;
    }

    @Override
    public Loadout loadoutOf(UUID player) {
        return null;
    }

    /** Texture of a cosmetic, by convention from its type and id. */
    static String assetOf(CosmeticType type, String id) {
        switch (type) {
            case CAPE:
                return "cosmetics/capes/" + id + ".png";
            default:
                return "cosmetics/" + type.name().toLowerCase(java.util.Locale.ROOT) + "/" + id + ".png";
        }
    }

    static List<Cosmetic> parse(JsonObject json) {
        List<Cosmetic> list = new ArrayList<Cosmetic>();
        for (JsonElement element : json.getAsJsonArray("cosmetics")) {
            JsonObject item = element.getAsJsonObject();
            try {
                CosmeticType type = CosmeticType.valueOf(item.get("type").getAsString());
                String id = item.get("id").getAsString();
                boolean seasonal = item.has("seasonal") && item.get("seasonal").getAsBoolean();
                Cosmetic.Access access = "grant".equals(item.get("access").getAsString()) ? Cosmetic.Access.GRANT : Cosmetic.Access.FREE;
                list.add(new Cosmetic(id, type, item.get("name").getAsString(), assetOf(type, id), seasonal, access));
            } catch (RuntimeException e) {
                Log.warn("Skipping cosmetic {}: {}", item, e.toString());
            }
        }
        return list;
    }

    private static List<Cosmetic> load() {
        InputStream in = BundledCosmetics.class.getResourceAsStream("/meridian/cosmetics/catalogue.json");
        if (in == null) {
            Log.warn("Cosmetics catalogue missing");
            return Collections.emptyList();
        }
        try {
            return parse(new JsonParser().parse(new InputStreamReader(in, Charset.forName("UTF-8"))).getAsJsonObject());
        } catch (RuntimeException e) {
            Log.error("Cosmetics catalogue unreadable", e);
            return Collections.emptyList();
        } finally {
            try {
                in.close();
            } catch (java.io.IOException ignored) {
                // read already
            }
        }
    }
}
