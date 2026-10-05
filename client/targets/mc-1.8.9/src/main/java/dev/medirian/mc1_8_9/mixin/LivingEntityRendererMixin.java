package dev.medirian.mc1_8_9.mixin;

import dev.medirian.platform.Hooks;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Hit Color for 1.8.9: the hurt flash is written into the brightness buffer as (1, 0, 0, 0.3) by
 * the first four {@code FloatBuffer.put} calls of {@code setBrightness} (method_10252).
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {

    private static final String PUT = "Ljava/nio/FloatBuffer;put(F)Ljava/nio/FloatBuffer;";

    @ModifyArg(method = "method_10252", at = @At(value = "INVOKE", target = PUT, ordinal = 0, remap = false))
    private float medirian$red(float vanilla) {
        int rgb = Hooks.hitColor();
        return rgb == 0 ? vanilla : ((rgb >> 16) & 0xFF) / 255f;
    }

    @ModifyArg(method = "method_10252", at = @At(value = "INVOKE", target = PUT, ordinal = 1, remap = false))
    private float medirian$green(float vanilla) {
        int rgb = Hooks.hitColor();
        return rgb == 0 ? vanilla : ((rgb >> 8) & 0xFF) / 255f;
    }

    @ModifyArg(method = "method_10252", at = @At(value = "INVOKE", target = PUT, ordinal = 2, remap = false))
    private float medirian$blue(float vanilla) {
        int rgb = Hooks.hitColor();
        return rgb == 0 ? vanilla : (rgb & 0xFF) / 255f;
    }

    @ModifyArg(method = "method_10252", at = @At(value = "INVOKE", target = PUT, ordinal = 3, remap = false))
    private float medirian$alpha(float vanilla) {
        return Hooks.hitColor() == 0 ? vanilla : Math.min(1f, vanilla * Hooks.hitColorIntensity());
    }
}
