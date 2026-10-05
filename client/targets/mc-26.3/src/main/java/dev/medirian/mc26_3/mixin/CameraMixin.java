package dev.medirian.mc26_3.mixin;

import dev.medirian.platform.Hooks;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Freelook: the camera uses Medirian's free rotation instead of the player's. */
@Mixin(Camera.class)
public abstract class CameraMixin {

    @Redirect(method = "alignWithEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getViewYRot(F)F"), require = 1, allow = 4)
    private float medirian$yaw(Entity entity, float partialTick) {
        return Hooks.freelookActive() ? Hooks.freelookYaw() : entity.getViewYRot(partialTick);
    }

    @Redirect(method = "alignWithEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getViewXRot(F)F"), require = 1, allow = 4)
    private float medirian$pitch(Entity entity, float partialTick) {
        return Hooks.freelookActive() ? Hooks.freelookPitch() : entity.getViewXRot(partialTick);
    }

    /** Zoom (FOV multiplier); the world FOV is also kept for projecting waypoint markers. */
    @Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
    private void medirian$fov(float partialTick, CallbackInfoReturnable<Float> cir) {
        double multiplier = Hooks.fovMultiplier();
        if (multiplier != 1.0) {
            cir.setReturnValue((float) (cir.getReturnValueF() * multiplier));
        }
        dev.medirian.mc26_3.ModernCamera.fov = cir.getReturnValueF();
    }
}
