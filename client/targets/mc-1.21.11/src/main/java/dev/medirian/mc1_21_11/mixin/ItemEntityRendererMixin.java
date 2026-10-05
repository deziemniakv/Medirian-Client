package dev.medirian.mc1_21_11.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.medirian.platform.Hooks;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.world.phys.AABB;
import org.joml.Quaternionfc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Item Physics: vanilla lifts the model by its bounding box plus a bobbing offset, then spins it.
 * Instead flat items lie down and blocks stand on the ground, turned by the entity's fixed random
 * bob offset (radians).
 */
@Mixin(ItemEntityRenderer.class)
public abstract class ItemEntityRendererMixin {

    private static final String SUBMIT = "submit(Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;"
            + "Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;"
            + "Lnet/minecraft/client/renderer/state/CameraRenderState;)V";

    /** Item models are 1/16 thick (1/32 on the ground); blocks and 3D models are much thicker. */
    @Unique
    private static boolean medirian$flat(AABB box) {
        return box.maxZ - box.minZ < 0.1;
    }

    @Redirect(method = SUBMIT, at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V"))
    private void medirian$position(PoseStack stack, float x, float y, float z, ItemEntityRenderState state,
                                   PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (!Hooks.itemPhysics()) {
            stack.translate(x, y, z);
            return;
        }
        AABB box = state.item.getModelBoundingBox();
        // flat: half its thickness above the ground once laid down; others: bottom on the ground
        float height = medirian$flat(box) ? (float) (box.maxZ - box.minZ) / 2f + 0.005f : (float) -box.minY;
        stack.translate(x, height, z);
    }

    @Redirect(method = SUBMIT, at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;mulPose(Lorg/joml/Quaternionfc;)V"))
    private void medirian$rotation(PoseStack stack, Quaternionfc spin, ItemEntityRenderState state,
                                   PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (!Hooks.itemPhysics()) {
            stack.mulPose(spin);
            return;
        }
        stack.mulPose(Axis.YP.rotation(state.bobOffset));
        if (medirian$flat(state.item.getModelBoundingBox())) {
            stack.mulPose(Axis.XP.rotationDegrees(90f));
        }
    }
}
