package dev.medirian.mc1_21_8.mixin;

import dev.medirian.platform.Hooks;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Fire Overlay: position and transparency of the flames shown while burning. */
@Mixin(ScreenEffectRenderer.class)
public abstract class ScreenEffectRendererMixin {

    @ModifyArg(method = "renderFire", index = 1,
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V"))
    private static float medirian$fireHeight(float y) {
        return y + Hooks.fireOverlayOffset();
    }

    @ModifyArg(method = "renderFire", index = 3,
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/VertexConsumer;setColor(FFFF)Lcom/mojang/blaze3d/vertex/VertexConsumer;"))
    private static float medirian$fireAlpha(float alpha) {
        return Hooks.fireOverlayAlpha(alpha);
    }
}
