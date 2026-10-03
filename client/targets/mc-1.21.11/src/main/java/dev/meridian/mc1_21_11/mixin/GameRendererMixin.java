package dev.meridian.mc1_21_11.mixin;

import dev.meridian.mc1_21_11.ModernCamera;
import dev.meridian.platform.Hooks;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Zoom (FOV multiplier) and world render timing. */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void meridian$fov(Camera camera, float partialTick, boolean useFovSetting, CallbackInfoReturnable<Float> cir) {
        double multiplier = Hooks.fovMultiplier();
        if (multiplier != 1.0) {
            cir.setReturnValue((float) (cir.getReturnValueF() * multiplier));
        }
        if (useFovSetting) {
            // the world projection's FOV (the hand uses its own): waypoint markers project with it
            ModernCamera.fov = cir.getReturnValueF();
        }
    }

    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void meridian$levelStart(DeltaTracker delta, CallbackInfo ci) {
        Hooks.worldRenderStart();
    }

    @Inject(method = "renderLevel", at = @At("RETURN"))
    private void meridian$levelEnd(DeltaTracker delta, CallbackInfo ci) {
        Hooks.worldRenderEnd();
    }
}
