package dev.medirian.mc1_21_8;

import dev.medirian.cosmetics.Cosmetic;
import dev.medirian.cosmetics.CosmeticRenderer;
import dev.medirian.cosmetics.CosmeticType;
import dev.medirian.platform.Hooks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;

/**
 * Medirian capes in 1.21.8: the player's skin gets Medirian's cape texture, so the game's cape
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

    /** {@code skin} with the player's Medirian cape, or null to keep it as it is. */
    public static PlayerSkin withCape(AbstractClientPlayer player, PlayerSkin skin) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean local = player == minecraft.player;
        // far away players' capes are not drawn (Settings → Advanced → Visibility)
        if (!local && !Hooks.cosmeticsVisible(minecraft.gameRenderer.getMainCamera().getPosition().distanceToSqr(player.position()))) {
            return null;
        }
        String asset = Hooks.capeTexture(player.getUUID(), local);
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
