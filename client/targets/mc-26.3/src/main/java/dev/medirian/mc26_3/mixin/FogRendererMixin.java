package dev.medirian.mc26_3.mixin;

import dev.medirian.platform.Hooks;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Settings → Advanced → Rendering → Fog: pushes the render-distance fog out (or off). Only the
 * distance fog moves; water, lava, powder snow, blindness and darkness fog stay as they are.
 */
@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {

    @Inject(method = "setupFog", at = @At("RETURN"))
    private void medirian$fog(CallbackInfoReturnable<FogData> cir) {
        float scale = Hooks.fogScale();
        FogData fog = cir.getReturnValue();
        if (scale != 1f && fog != null) {
            fog.renderDistanceStart *= scale;
            fog.renderDistanceEnd *= scale;
        }
    }
}
