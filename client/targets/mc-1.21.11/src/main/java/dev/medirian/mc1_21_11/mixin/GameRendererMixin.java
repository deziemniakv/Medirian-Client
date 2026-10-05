package dev.medirian.mc1_21_11.mixin;

import dev.medirian.mc1_21_11.ModernCamera;
import dev.medirian.platform.Hooks;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Zoom (FOV multiplier), hurt camera and world render timing. */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void medirian$fov(Camera camera, float partialTick, boolean useFovSetting, CallbackInfoReturnable<Float> cir) {
        double multiplier = Hooks.fovMultiplier();
        if (multiplier != 1.0) {
            cir.setReturnValue((float) (cir.getReturnValueF() * multiplier));
        }
        if (useFovSetting) {
            // the world projection's FOV (the hand uses its own): waypoint markers project with it
            ModernCamera.fov = cir.getReturnValueF();
        }
    }

    /** Hurt Camera: the roll of the hurt tilt (ordinal 0 is the death animation, 1 and 3 turn to the hit direction and back). */
    @ModifyArg(method = "bobHurt", index = 0,
            at = @At(value = "INVOKE", target = "Lcom/mojang/math/Axis;rotationDegrees(F)Lorg/joml/Quaternionf;", ordinal = 2))
    private float medirian$hurtTilt(float degrees) {
        return degrees * Hooks.hurtCameraStrength();
    }

    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void medirian$levelStart(DeltaTracker delta, CallbackInfo ci) {
        Hooks.worldRenderStart();
    }

    @Inject(method = "renderLevel", at = @At("RETURN"))
    private void medirian$levelEnd(DeltaTracker delta, CallbackInfo ci) {
        Hooks.worldRenderEnd();
    }
}
