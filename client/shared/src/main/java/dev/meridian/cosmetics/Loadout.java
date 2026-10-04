package dev.meridian.cosmetics;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.EnumMap;
import java.util.Map;

/** Equipped cosmetic id per {@link CosmeticType}. */
public final class Loadout {

    private final Map<CosmeticType, String> equipped = new EnumMap<CosmeticType, String>(CosmeticType.class);

    public String equipped(CosmeticType type) {
        return equipped.get(type);
    }

    public void equip(CosmeticType type, String cosmeticId) {
        if (cosmeticId == null) {
            equipped.remove(type);
        } else {
            equipped.put(type, cosmeticId);
        }
    }

    public boolean isEmpty() {
        return equipped.isEmpty();
    }

    public Loadout copy() {
        Loadout copy = new Loadout();
        copy.equipped.putAll(equipped);
        return copy;
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        for (Map.Entry<CosmeticType, String> entry : equipped.entrySet()) {
            json.addProperty(entry.getKey().name(), entry.getValue());
        }
        return json;
    }

    public static Loadout fromJson(JsonObject json) {
        Loadout loadout = new Loadout();
        if (json == null) {
            return loadout;
        }
        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
            try {
                loadout.equip(CosmeticType.valueOf(entry.getKey()), entry.getValue().getAsString());
            } catch (RuntimeException ignored) {
                // unknown type from a newer version
            }
        }
        return loadout;
    }
}
