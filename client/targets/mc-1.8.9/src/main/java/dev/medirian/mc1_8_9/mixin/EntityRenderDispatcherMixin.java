package dev.medirian.mc1_8_9.mixin;

import dev.medirian.perf.CullState;
import dev.medirian.platform.Hooks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.CameraView;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Entity Culling: distance and occlusion culling of entities that passed vanilla's frustum check. */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {

    /**
     * {@code viewX/Y/Z} is the view entity's (interpolated) feet position; the camera sits at
     * {@link Camera#getPosition()} relative to it (eye height, third person, freelook).
     */
    @Inject(method = "shouldRender", at = @At("RETURN"), cancellable = true)
    private void medirian$cull(Entity entity, CameraView camera, double viewX, double viewY, double viewZ,
                               CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() || entity == MinecraftClient.getInstance().getCameraEntity()) {
            return;
        }
        Vec3d offset = Camera.getPosition();
        Box box = entity.getBoundingBox();
        if (!Hooks.shouldRenderEntity((CullState) entity, entity instanceof PlayerEntity,
                viewX + offset.x, viewY + offset.y, viewZ + offset.z,
                box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ)) {
            cir.setReturnValue(false);
        }
    }
}
