package dev.medirian.mc1_8_9.mixin;

import dev.medirian.platform.Hooks;
import net.minecraft.client.render.item.HeldItemRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Fire Overlay for 1.8.9: position and transparency of the flames shown while burning. */
@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererMixin {

    @ModifyArg(method = "renderFireOverlay", index = 1,
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/GlStateManager;translate(FFF)V"))
    private float medirian$fireHeight(float y) {
        return y + Hooks.fireOverlayOffset();
    }

    @ModifyArg(method = "renderFireOverlay", index = 3,
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/GlStateManager;color(FFFF)V", ordinal = 0))
    private float medirian$fireAlpha(float alpha) {
        return Hooks.fireOverlayAlpha(alpha);
    }
}
