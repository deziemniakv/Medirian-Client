package dev.meridian.mc1_8_9.mixin;

import dev.meridian.platform.Hooks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.CameraView;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Entity Culling: skip rendering entities beyond the configured distance. */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {

    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void meridian$cull(Entity entity, CameraView camera, double camX, double camY, double camZ,
                               CallbackInfoReturnable<Boolean> cir) {
        if (entity == MinecraftClient.getInstance().getCameraEntity()) {
            return;
        }
        if (!Hooks.shouldRenderEntity(entity.squaredDistanceTo(camX, camY, camZ), entity instanceof PlayerEntity)) {
            cir.setReturnValue(false);
        }
    }
}
