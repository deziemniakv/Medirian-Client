package dev.meridian.cosmetics;

/**
 * Version-specific renderer for one {@link CosmeticType}, registered by an adapter through
 * {@link CosmeticsManager#registerRenderer}. Cosmetic types without a renderer are not offered
 * in the UI.
 *
 * <p>Capes: both adapters hand the texture from {@link CosmeticsManager#capeTexture} to the
 * game's own cape rendering (1.8.9: the cape getter of the player, 1.21.11: the player's skin).
 */
public interface CosmeticRenderer {

    CosmeticType type();

    /** Called when the equipped cosmetic of this type changes (load textures etc.). */
    void onEquipped(Cosmetic cosmetic);
}
