package dev.meridian.cosmetics;

/**
 * Version-specific renderer for one {@link CosmeticType}, registered by an adapter through
 * {@link CosmeticsManager#registerRenderer}. Cosmetic types without a renderer are not offered
 * in the UI.
 *
 * <p>TODO(cosmetics-render): implement CAPE first (1.8.9: AbstractClientPlayer#getLocationCape,
 * modern: PlayerSkin cape texture), then WINGS/HAT as layer renderers.
 */
public interface CosmeticRenderer {

    CosmeticType type();

    /** Called when the equipped cosmetic of this type changes (load textures etc.). */
    void onEquipped(Cosmetic cosmetic);
}
