package dev.medirian.mc1_21_11.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.medirian.platform.Hooks;
import dev.medirian.waypoint.Waypoint;
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

import java.util.List;

/**
 * Block Overlay (outline colour/width and an optional translucent fill of the selected block) and
 * waypoint beams.
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {

    private static final double BEAM_HALF_WIDTH = 0.12;
    private static final double BEAM_HEIGHT = 256;

    private static final String HIT_OUTLINE = "Lnet/minecraft/client/renderer/LevelRenderer;renderHitOutline("
            + "Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;DDD"
            + "Lnet/minecraft/client/renderer/state/BlockOutlineRenderState;IF)V";

    // ordinal 0 is the extra black outline of the high-contrast accessibility option; ordinal 1 is the outline itself
    @ModifyArg(method = "renderBlockOutline", at = @At(value = "INVOKE", target = HIT_OUTLINE, ordinal = 1), index = 6)
    private int medirian$outlineColor(int vanilla) {
        return Hooks.blockOutlineColor(vanilla);
    }

    @ModifyArg(method = "renderBlockOutline", at = @At(value = "INVOKE", target = HIT_OUTLINE, ordinal = 1), index = 7)
    private float medirian$outlineWidth(float vanilla) {
        return Hooks.blockOutlineWidth(vanilla);
    }

    /**
     * Waypoint beams, drawn in the translucent pass. renderBlockOutline runs every frame (it returns
     * early only when no block is selected, after this point), so its head is a per-frame hook with
     * the world pose and buffers at hand.
     */
    @Inject(method = "renderBlockOutline", at = @At("HEAD"))
    private void medirian$beams(MultiBufferSource.BufferSource buffers, PoseStack poseStack, boolean translucent,
                                LevelRenderState state, CallbackInfo ci) {
        if (!translucent) {
            return;
        }
        List<Waypoint> beams = Hooks.waypointBeams();
        if (beams.isEmpty()) {
            return;
        }
        Vec3 camera = state.cameraRenderState.pos;
        VertexConsumer consumer = buffers.getBuffer(RenderTypes.debugQuads());
        PoseStack.Pose pose = poseStack.last();
        double w = BEAM_HALF_WIDTH;
        for (Waypoint waypoint : beams) {
            int argb = (waypoint.color & 0xFFFFFF) | 0x66000000;
            double cx = waypoint.x + 0.5 - camera.x;
            double cz = waypoint.z + 0.5 - camera.z;
            double y0 = waypoint.y - camera.y;
            double y1 = y0 + BEAM_HEIGHT;
            beamSide(consumer, pose, argb, cx - w, cz - w, cx + w, cz - w, y0, y1);
            beamSide(consumer, pose, argb, cx + w, cz - w, cx + w, cz + w, y0, y1);
            beamSide(consumer, pose, argb, cx + w, cz + w, cx - w, cz + w, y0, y1);
            beamSide(consumer, pose, argb, cx - w, cz + w, cx - w, cz - w, y0, y1);
        }
        buffers.endBatch(RenderTypes.debugQuads());
    }

    /** One vertical side in both windings, so it shows whichever way face culling is set. */
    private static void beamSide(VertexConsumer consumer, PoseStack.Pose pose, int argb,
                                 double x1, double z1, double x2, double z2, double y0, double y1) {
        quad(consumer, pose, argb, x1, y0, z1, x2, y0, z2, x2, y1, z2, x1, y1, z1);
        quad(consumer, pose, argb, x1, y1, z1, x2, y1, z2, x2, y0, z2, x1, y0, z1);
    }

    /** Draws the fill after the outline, right before vanilla flushes the batch. */
    @Inject(method = "renderBlockOutline", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;endLastBatch()V"))
    private void medirian$fill(MultiBufferSource.BufferSource buffers, PoseStack poseStack, boolean translucent,
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
