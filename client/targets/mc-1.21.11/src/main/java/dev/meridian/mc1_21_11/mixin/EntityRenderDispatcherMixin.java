package dev.meridian.mc1_21_11.mixin;

import dev.meridian.platform.Hooks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Entity Culling: skip rendering entities beyond the configured distance. */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {

    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private <E extends Entity> void meridian$cull(E entity, Frustum frustum, double camX, double camY, double camZ,
                                                  CallbackInfoReturnable<Boolean> cir) {
        if (entity == Minecraft.getInstance().getCameraEntity()) {
            return;
        }
        if (!Hooks.shouldRenderEntity(entity.distanceToSqr(camX, camY, camZ), entity instanceof Player)) {
            cir.setReturnValue(false);
        }
    }
}
