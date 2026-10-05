package dev.medirian.mc1_21_11;

import com.mojang.blaze3d.platform.NativeImage;
import dev.medirian.mc1_21_11.mixin.OverlayTextureAccessor;
import dev.medirian.platform.Hooks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;

/**
 * Hit Color for modern versions: the hurt flash comes from the upper half (rows 0–7) of the
 * 16×16 {@code OverlayTexture}. When the configured colour changes, those rows are rewritten and
 * the texture re-uploaded — no per-entity cost at all.
 */
final class HitColorTexture {

    /** Vanilla hurt colour: red at 70% alpha. */
    private static final int VANILLA = 0xB2FF0000;
    private static final int VANILLA_ALPHA = 0xB2;

    private static int applied = VANILLA;

    private HitColorTexture() {
    }

    static void update(Minecraft minecraft) {
        int rgb = Hooks.hitColor();
        int wanted = VANILLA;
        if (rgb != 0) {
            int alpha = Math.max(0, Math.min(255, Math.round(VANILLA_ALPHA * Hooks.hitColorIntensity())));
            wanted = (alpha << 24) | (rgb & 0x00FFFFFF);
        }
        if (wanted == applied) {
            return;
        }
        DynamicTexture texture = ((OverlayTextureAccessor) minecraft.gameRenderer.overlayTexture()).medirian$texture();
        NativeImage pixels = texture.getPixels();
        if (pixels == null) {
            return;
        }
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 16; x++) {
                pixels.setPixel(x, y, wanted);
            }
        }
        texture.upload();
        applied = wanted;
    }
}
