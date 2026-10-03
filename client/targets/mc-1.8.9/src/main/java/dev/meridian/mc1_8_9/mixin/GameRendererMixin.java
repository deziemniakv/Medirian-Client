package dev.meridian.mc1_8_9.mixin;

import dev.meridian.mc1_8_9.LegacyCamera;
import dev.meridian.platform.Hooks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.ClientPlayerEntity;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Zoom (FOV), freelook (mouse turn + camera), fullbright (gamma) and world render timing. */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void meridian$fov(float tickDelta, boolean changingFov, CallbackInfoReturnable<Float> cir) {
        double multiplier = Hooks.fovMultiplier();
        if (multiplier != 1.0) {
            cir.setReturnValue((float) (cir.getReturnValueF() * multiplier));
        }
        if (changingFov) {
            // the world projection's FOV (the hand uses its own): waypoint markers project with it
            LegacyCamera.fov = cir.getReturnValueF();
            LegacyCamera.tickDelta = tickDelta;
        }
    }

    @Redirect(method = "render(FJ)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/ClientPlayerEntity;increaseTransforms(FF)V"))
    private void meridian$turn(ClientPlayerEntity player, float yaw, float pitch) {
        if (Hooks.freelookActive()) {
            Hooks.freelookTurn(yaw, pitch);
        } else {
            player.increaseTransforms(yaw, pitch);
        }
    }

    // --- freelook camera: transformCamera reads the camera entity's rotation fields ---

    @Redirect(method = "transformCamera", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/Entity;yaw:F", opcode = Opcodes.GETFIELD))
    private float meridian$yaw(Entity entity) {
        return freelook(entity) ? Hooks.freelookYaw() : entity.yaw;
    }

    @Redirect(method = "transformCamera", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/Entity;prevYaw:F", opcode = Opcodes.GETFIELD))
    private float meridian$prevYaw(Entity entity) {
        return freelook(entity) ? Hooks.freelookYaw() : entity.prevYaw;
    }

    @Redirect(method = "transformCamera", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/Entity;pitch:F", opcode = Opcodes.GETFIELD))
    private float meridian$pitch(Entity entity) {
        return freelook(entity) ? Hooks.freelookPitch() : entity.pitch;
    }

    @Redirect(method = "transformCamera", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/Entity;prevPitch:F", opcode = Opcodes.GETFIELD))
    private float meridian$prevPitch(Entity entity) {
        return freelook(entity) ? Hooks.freelookPitch() : entity.prevPitch;
    }

    private static boolean freelook(Entity entity) {
        return Hooks.freelookActive() && entity == MinecraftClient.getInstance().getCameraEntity();
    }

    @Redirect(method = "updateLightmap", at = @At(value = "FIELD", target = "Lnet/minecraft/client/option/GameOptions;gamma:F", opcode = Opcodes.GETFIELD))
    private float meridian$gamma(GameOptions options) {
        return (float) Hooks.gamma(options.gamma);
    }

    @Inject(method = "renderWorld(FJ)V", at = @At("HEAD"))
    private void meridian$worldStart(float tickDelta, long limitTime, CallbackInfo ci) {
        Hooks.worldRenderStart();
    }

    @Inject(method = "renderWorld(FJ)V", at = @At("RETURN"))
    private void meridian$worldEnd(float tickDelta, long limitTime, CallbackInfo ci) {
        Hooks.worldRenderEnd();
    }
}
