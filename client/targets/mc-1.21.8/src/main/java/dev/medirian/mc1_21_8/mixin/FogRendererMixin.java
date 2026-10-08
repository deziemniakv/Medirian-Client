package dev.medirian.mc1_21_8.mixin;

import dev.medirian.platform.Hooks;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Settings → Advanced → Rendering → Fog: pushes the render-distance fog out (or off). Only the
 * distance fog moves; water, lava, powder snow, blindness and darkness fog stay as they are.
 */
@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {

    private static final String UPDATE_BUFFER = "Lnet/minecraft/client/renderer/fog/FogRenderer;updateBuffer(Ljava/nio/ByteBuffer;ILorg/joml/Vector4f;FFFFFF)V";

    @ModifyArg(method = "setupFog", at = @At(value = "INVOKE", target = UPDATE_BUFFER), index = 5)
    private float medirian$fogStart(float renderDistanceStart) {
        return renderDistanceStart * Hooks.fogScale();
    }

    @ModifyArg(method = "setupFog", at = @At(value = "INVOKE", target = UPDATE_BUFFER), index = 6)
    private float medirian$fogEnd(float renderDistanceEnd) {
        return renderDistanceEnd * Hooks.fogScale();
    }
}
