package dev.meridian.mc26_3.mixin;

import dev.meridian.platform.Hooks;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Fire Overlay: lower and fade the burning overlay. */
@Mixin(ScreenEffectRenderer.class)
public abstract class ScreenEffectRendererMixin {

    private static final String SPRITE_QUAD = "Lnet/minecraft/client/renderer/ScreenEffectRenderer;buildSpriteQuad("
            + "Lcom/mojang/blaze3d/vertex/VertexConsumer;Lorg/joml/Matrix4f;"
            + "Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;FFFFFI)V";

    @ModifyArg(method = "buildFireQuad", index = 4, at = @At(value = "INVOKE", target = SPRITE_QUAD))
    private static float meridian$fireBottom(float y) {
        return y + Hooks.fireOverlayOffset();
    }

    @ModifyArg(method = "buildFireQuad", index = 6, at = @At(value = "INVOKE", target = SPRITE_QUAD))
    private static float meridian$fireTop(float y) {
        return y + Hooks.fireOverlayOffset();
    }

    @ModifyArg(method = "buildFireQuad", index = 8, at = @At(value = "INVOKE", target = SPRITE_QUAD))
    private static int meridian$fireAlpha(int argb) {
        float alpha = Hooks.fireOverlayAlpha((argb >>> 24) / 255f);
        return (Math.round(Math.max(0f, Math.min(1f, alpha)) * 255f) << 24) | (argb & 0xFFFFFF);
    }
}
