package dev.meridian.mc1_8_9;

import dev.meridian.cosmetics.Cosmetic;
import dev.meridian.cosmetics.CosmeticRenderer;
import dev.meridian.cosmetics.CosmeticType;
import dev.meridian.platform.Hooks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.util.Identifier;

/**
 * Meridian capes in 1.8.9: the player's cape getter returns Meridian's texture, and the game's own
 * cape renderer (with its swing physics) draws it.
 */
public final class LegacyCapes {

    /** Declares cape support; textures load on first use. */
    public static final CosmeticRenderer RENDERER = new CosmeticRenderer() {
        @Override
        public CosmeticType type() {
            return CosmeticType.CAPE;
        }

        @Override
        public void onEquipped(Cosmetic cosmetic) {
            Textures.get(MinecraftClient.getInstance(), cosmetic.asset());
        }
    };

    private LegacyCapes() {
    }

    /** The Meridian cape texture of {@code player}, or null to keep the vanilla one. */
    public static Identifier texture(AbstractClientPlayerEntity player) {
        MinecraftClient client = MinecraftClient.getInstance();
        String asset = Hooks.capeTexture(player.getUuid(), player == client.player);
        if (asset == null) {
            return null;
        }
        Textures.Entry entry = Textures.get(client, asset);
        return entry == null ? null : entry.id;
    }
}
