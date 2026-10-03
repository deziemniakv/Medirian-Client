package dev.meridian.cosmetics;

/**
 * Definition of a cosmetic item. Assets are referenced by id; version adapters resolve them to
 * textures/models in their {@link CosmeticRenderer}.
 */
public final class Cosmetic {

    private final String id;
    private final CosmeticType type;
    private final String name;
    private final String asset;
    private final boolean seasonal;

    public Cosmetic(String id, CosmeticType type, String name, String asset, boolean seasonal) {
        this.id = id;
        this.type = type;
        this.name = name;
        this.asset = asset;
        this.seasonal = seasonal;
    }

    public String id() {
        return id;
    }

    public CosmeticType type() {
        return type;
    }

    public String name() {
        return name;
    }

    /** Asset path, e.g. {@code cosmetics/capes/moonlit.png}. */
    public String asset() {
        return asset;
    }

    public boolean seasonal() {
        return seasonal;
    }
}
