package dev.meridian.mc1_21_11.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.meridian.platform.Hooks;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.BlockOutlineRenderState;
import net.minecraft.client.renderer.state.LevelRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Block Overlay: outline colour/width and an optional translucent fill of the selected block. */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {

    private static final String HIT_OUTLINE = "Lnet/minecraft/client/renderer/LevelRenderer;renderHitOutline("
            + "Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;DDD"
            + "Lnet/minecraft/client/renderer/state/BlockOutlineRenderState;IF)V";

    // ordinal 0 is the extra black outline of the high-contrast accessibility option; ordinal 1 is the outline itself
    @ModifyArg(method = "renderBlockOutline", at = @At(value = "INVOKE", target = HIT_OUTLINE, ordinal = 1), index = 6)
    private int meridian$outlineColor(int vanilla) {
        return Hooks.blockOutlineColor(vanilla);
    }

    @ModifyArg(method = "renderBlockOutline", at = @At(value = "INVOKE", target = HIT_OUTLINE, ordinal = 1), index = 7)
    private float meridian$outlineWidth(float vanilla) {
        return Hooks.blockOutlineWidth(vanilla);
    }

    /** Draws the fill after the outline, right before vanilla flushes the batch. */
    @Inject(method = "renderBlockOutline", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;endLastBatch()V"))
    private void meridian$fill(MultiBufferSource.BufferSource buffers, PoseStack poseStack, boolean translucent,
                               LevelRenderState state, CallbackInfo ci) {
        int argb = Hooks.blockOutlineFill();
        BlockOutlineRenderState outline = state.blockOutlineRenderState;
        if (argb == 0 || outline == null) {
            return;
        }
        Vec3 camera = state.cameraRenderState.pos;
        BlockPos pos = outline.pos();
        VertexConsumer consumer = buffers.getBuffer(RenderTypes.debugQuads());
        PoseStack.Pose pose = poseStack.last();
        for (AABB box : outline.shape().toAabbs()) {
            AABB b = box.inflate(0.002).move(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);
            quad(consumer, pose, argb, b.minX, b.minY, b.minZ, b.maxX, b.minY, b.minZ, b.maxX, b.minY, b.maxZ, b.minX, b.minY, b.maxZ);
            quad(consumer, pose, argb, b.minX, b.maxY, b.minZ, b.minX, b.maxY, b.maxZ, b.maxX, b.maxY, b.maxZ, b.maxX, b.maxY, b.minZ);
            quad(consumer, pose, argb, b.minX, b.minY, b.minZ, b.minX, b.maxY, b.minZ, b.maxX, b.maxY, b.minZ, b.maxX, b.minY, b.minZ);
            quad(consumer, pose, argb, b.minX, b.minY, b.maxZ, b.maxX, b.minY, b.maxZ, b.maxX, b.maxY, b.maxZ, b.minX, b.maxY, b.maxZ);
            quad(consumer, pose, argb, b.minX, b.minY, b.minZ, b.minX, b.minY, b.maxZ, b.minX, b.maxY, b.maxZ, b.minX, b.maxY, b.minZ);
            quad(consumer, pose, argb, b.maxX, b.minY, b.minZ, b.maxX, b.maxY, b.minZ, b.maxX, b.maxY, b.maxZ, b.maxX, b.minY, b.maxZ);
        }
    }

    private static void quad(VertexConsumer consumer, PoseStack.Pose pose, int argb,
                             double x1, double y1, double z1, double x2, double y2, double z2,
                             double x3, double y3, double z3, double x4, double y4, double z4) {
        consumer.addVertex(pose, (float) x1, (float) y1, (float) z1).setColor(argb);
        consumer.addVertex(pose, (float) x2, (float) y2, (float) z2).setColor(argb);
        consumer.addVertex(pose, (float) x3, (float) y3, (float) z3).setColor(argb);
        consumer.addVertex(pose, (float) x4, (float) y4, (float) z4).setColor(argb);
    }
}
