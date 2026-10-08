package dev.medirian.mc26_3.mixin;

import dev.medirian.mc26_3.ModernCamera;
import dev.medirian.mc26_3.ModernPlatform;
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

/** Zoom (FOV multiplier), hurt camera, world render timing and the Liquid Glass backdrop. */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    /** Hurt Camera: the roll of the hurt tilt (ordinal 0 is the death animation, 1 and 3 turn to the hit direction and back). */
    @ModifyArg(method = "bobHurt", index = 1,
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;rotateDegrees(Lcom/mojang/math/Axis;F)V", ordinal = 2))
    private float medirian$hurtTilt(float degrees) {
        return degrees * Hooks.hurtCameraStrength();
    }

    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void medirian$levelStart(CallbackInfo ci) {
        Hooks.worldRenderStart();
    }

    @Inject(method = "renderLevel", at = @At("RETURN"))
    private void medirian$levelEnd(CallbackInfo ci) {
        Hooks.worldRenderEnd();
    }

    /** The world is finished: blur it for Liquid Glass HUD widgets before the GUI is drawn over it. */
    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/render/GuiRenderer;render()V"))
    private void medirian$backdrop(CallbackInfo ci) {
        ModernPlatform platform = ModernPlatform.get();
        if (platform != null) {
            platform.gfx().renderBackdrop();
        }
    }
}
