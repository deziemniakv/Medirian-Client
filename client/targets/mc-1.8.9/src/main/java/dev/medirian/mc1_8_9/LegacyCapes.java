package dev.medirian.mc1_8_9;

import dev.medirian.cosmetics.Cosmetic;
import dev.medirian.cosmetics.CosmeticRenderer;
import dev.medirian.cosmetics.CosmeticType;
import dev.medirian.platform.Hooks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.util.Identifier;

/**
 * Medirian capes in 1.8.9: the player's cape getter returns Medirian's texture, and the game's own
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

    /** The Medirian cape texture of {@code player}, or null to keep the vanilla one. */
    public static Identifier texture(AbstractClientPlayerEntity player) {
        MinecraftClient client = MinecraftClient.getInstance();
        boolean local = player == client.player;
        // far away players' capes are not drawn (Settings → Advanced → Visibility)
        if (!local && client.getCameraEntity() != null && !Hooks.cosmeticsVisible(player.squaredDistanceTo(client.getCameraEntity()))) {
            return null;
        }
        String asset = Hooks.capeTexture(player.getUuid(), local);
        if (asset == null) {
            return null;
        }
        Textures.Entry entry = Textures.get(client, asset);
        return entry == null ? null : entry.id;
    }
}
