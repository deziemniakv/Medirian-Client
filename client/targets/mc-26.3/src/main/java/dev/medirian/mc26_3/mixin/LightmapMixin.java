package dev.medirian.mc26_3.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.medirian.platform.Hooks;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Slice;

/** Fullbright: overrides the gamma value fed into the lightmap. */
@Mixin(LightmapRenderStateExtractor.class)
public abstract class LightmapMixin {

    @ModifyExpressionValue(method = "extract",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;", ordinal = 0),
            slice = @Slice(from = @At(value = "INVOKE", target = "Lnet/minecraft/client/Options;gamma()Lnet/minecraft/client/OptionInstance;")))
    private Object medirian$gamma(Object gamma) {
        double vanilla = (Double) gamma;
        double value = Hooks.gamma(vanilla);
        return value == vanilla ? gamma : (Object) value;
    }
}
