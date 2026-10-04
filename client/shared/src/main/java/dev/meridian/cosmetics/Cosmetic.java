package dev.meridian.cosmetics;

/**
 * Definition of a cosmetic item. Assets are bundled with the client and referenced by path;
 * version adapters turn them into textures.
 */
public final class Cosmetic {

    /** Who may use a cosmetic: everybody, or players the services granted it to. */
    public enum Access { FREE, GRANT }

    private final String id;
    private final CosmeticType type;
    private final String name;
    private final String asset;
    private final boolean seasonal;
    private final Access access;

    public Cosmetic(String id, CosmeticType type, String name, String asset, boolean seasonal, Access access) {
        this.id = id;
        this.type = type;
        this.name = name;
        this.asset = asset;
        this.seasonal = seasonal;
        this.access = access;
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

    /** Asset path relative to {@code assets/meridian/textures/}, e.g. {@code cosmetics/capes/cape_moonlit.png}. */
    public String asset() {
        return asset;
    }

    public boolean seasonal() {
        return seasonal;
    }

    public Access access() {
        return access;
    }
}
