package dev.meridian.mc1_21_8;

import dev.meridian.cosmetics.Cosmetic;
import dev.meridian.cosmetics.CosmeticRenderer;
import dev.meridian.cosmetics.CosmeticType;
import dev.meridian.platform.Hooks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;

/**
 * Meridian capes in 1.21.8: the player's skin gets Meridian's cape texture, so the game's cape
 * layer (and the elytra, which falls back to the cape texture) draws it.
 */
public final class ModernCapes {

    /** Declares cape support; textures load on first use. */
    public static final CosmeticRenderer RENDERER = new CosmeticRenderer() {
        @Override
        public CosmeticType type() {
            return CosmeticType.CAPE;
        }

        @Override
        public void onEquipped(Cosmetic cosmetic) {
            texture(cosmetic.asset());
        }
    };

    private ModernCapes() {
    }

    /** {@code skin} with the player's Meridian cape, or null to keep it as it is. */
    public static PlayerSkin withCape(AbstractClientPlayer player, PlayerSkin skin) {
        String asset = Hooks.capeTexture(player.getUUID(), player == Minecraft.getInstance().player);
        if (asset == null || skin == null) {
            return null;
        }
        ResourceLocation cape = texture(asset);
        return cape == null ? null
                : new PlayerSkin(skin.texture(), skin.textureUrl(), cape, skin.elytraTexture(), skin.model(), skin.secure());
    }

    private static ResourceLocation texture(String asset) {
        Textures.Entry entry = Textures.get(Minecraft.getInstance(), asset);
        return entry == null ? null : entry.id();
    }
}
