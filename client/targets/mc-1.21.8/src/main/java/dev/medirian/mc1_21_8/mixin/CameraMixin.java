package dev.medirian.mc1_21_8.mixin;

import dev.medirian.platform.Hooks;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Freelook: the camera uses Medirian's free rotation instead of the player's. */
@Mixin(Camera.class)
public abstract class CameraMixin {

    @Redirect(method = "setup", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getViewYRot(F)F"), require = 1, allow = 4)
    private float medirian$yaw(Entity entity, float partialTick) {
        return Hooks.freelookActive() ? Hooks.freelookYaw() : entity.getViewYRot(partialTick);
    }

    @Redirect(method = "setup", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getViewXRot(F)F"), require = 1, allow = 4)
    private float medirian$pitch(Entity entity, float partialTick) {
        return Hooks.freelookActive() ? Hooks.freelookPitch() : entity.getViewXRot(partialTick);
    }
}
