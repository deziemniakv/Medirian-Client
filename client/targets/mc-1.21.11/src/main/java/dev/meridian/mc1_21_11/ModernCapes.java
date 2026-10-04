package dev.meridian.mc1_21_11;

import dev.meridian.cosmetics.Cosmetic;
import dev.meridian.cosmetics.CosmeticRenderer;
import dev.meridian.cosmetics.CosmeticType;
import dev.meridian.platform.Hooks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.ClientAsset;
import net.minecraft.world.entity.player.PlayerSkin;

import java.util.HashMap;
import java.util.Map;

/**
 * Meridian capes in 1.21.11: the player's skin gets Meridian's cape texture, so the game's cape
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

    private static final Map<String, ClientAsset.Texture> TEXTURES = new HashMap<>();

    private ModernCapes() {
    }

    /** {@code skin} with the player's Meridian cape, or null to keep it as it is. */
    public static PlayerSkin withCape(AbstractClientPlayer player, PlayerSkin skin) {
        String asset = Hooks.capeTexture(player.getUUID(), player == Minecraft.getInstance().player);
        if (asset == null || skin == null) {
            return null;
        }
        ClientAsset.Texture cape = texture(asset);
        return cape == null ? null : new PlayerSkin(skin.body(), cape, skin.elytra(), skin.model(), skin.secure());
    }

    private static ClientAsset.Texture texture(String asset) {
        ClientAsset.Texture texture = TEXTURES.get(asset);
        if (texture == null) {
            Textures.Entry entry = Textures.get(Minecraft.getInstance(), asset);
            if (entry == null) {
                return null;
            }
            texture = new ClientAsset.ResourceTexture(entry.id(), entry.id());
            TEXTURES.put(asset, texture);
        }
        return texture;
    }
}
