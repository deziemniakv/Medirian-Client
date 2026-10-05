package dev.medirian.mc26_3.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.medirian.platform.Hooks;
import dev.medirian.waypoint.Waypoint;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.BlockOutlineRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Block Overlay (outline colour/width and an optional translucent fill of the selected block) and
 * waypoint beams. 26.x submits world geometry to a {@link SubmitNodeCollector} instead of drawing
 * into buffers, so both are submitted as custom geometry.
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {

    private static final double BEAM_HALF_WIDTH = 0.12;
    private static final double BEAM_HEIGHT = 256;

    private static final String HIT_OUTLINE = "Lnet/minecraft/client/renderer/LevelRenderer;submitHitOutline("
            + "Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;"
            + "Lnet/minecraft/client/renderer/rendertype/RenderType;"
            + "Lnet/minecraft/client/renderer/state/level/BlockOutlineRenderState;IFZ)V";

    // ordinal 0 is the extra outline of the high-contrast accessibility option; ordinal 1 is the outline itself
    @ModifyArg(method = "submitBlockOutline", at = @At(value = "INVOKE", target = HIT_OUTLINE, ordinal = 1), index = 4)
    private int medirian$outlineColor(int vanilla) {
        return Hooks.blockOutlineColor(vanilla);
    }

    @ModifyArg(method = "submitBlockOutline", at = @At(value = "INVOKE", target = HIT_OUTLINE, ordinal = 1), index = 5)
    private float medirian$outlineWidth(float vanilla) {
        return Hooks.blockOutlineWidth(vanilla);
    }

    /**
     * Waypoint beams. submitBlockOutline runs once per frame and returns early only when no block
     * is selected, after its head, so the head is a per-frame hook with the world pose at hand.
     */
    @Inject(method = "submitBlockOutline", at = @At("HEAD"))
    private void medirian$beams(PoseStack poseStack, SubmitNodeCollector collector, LevelRenderState state, CallbackInfo ci) {
        List<Waypoint> beams = Hooks.waypointBeams();
        if (beams.isEmpty()) {
            return;
        }
        Vec3 camera = state.cameraRenderState.pos;
        // the collector draws later: copy what the geometry needs
        final List<double[]> columns = new ArrayList<>(beams.size());
        for (Waypoint waypoint : beams) {
            columns.add(new double[] {waypoint.x + 0.5 - camera.x, waypoint.y - camera.y, waypoint.z + 0.5 - camera.z,
                    (waypoint.color & 0xFFFFFF) | 0x66000000});
        }
        collector.submitCustomGeometry(poseStack, RenderTypes.debugQuads(), (pose, consumer) -> {
            double w = BEAM_HALF_WIDTH;
            for (double[] c : columns) {
                int argb = (int) (long) c[3];
                double cx = c[0];
                double cz = c[2];
                double y0 = c[1];
                double y1 = y0 + BEAM_HEIGHT;
                beamSide(consumer, pose, argb, cx - w, cz - w, cx + w, cz - w, y0, y1);
                beamSide(consumer, pose, argb, cx + w, cz - w, cx + w, cz + w, y0, y1);
                beamSide(consumer, pose, argb, cx + w, cz + w, cx - w, cz + w, y0, y1);
                beamSide(consumer, pose, argb, cx - w, cz + w, cx - w, cz - w, y0, y1);
            }
        });
    }

    /** One vertical side in both windings, so it shows whichever way face culling is set. */
    private static void beamSide(VertexConsumer consumer, PoseStack.Pose pose, int argb,
                                 double x1, double z1, double x2, double z2, double y0, double y1) {
        quad(consumer, pose, argb, x1, y0, z1, x2, y0, z2, x2, y1, z2, x1, y1, z1);
        quad(consumer, pose, argb, x1, y1, z1, x2, y1, z2, x2, y0, z2, x1, y0, z1);
    }

    /** The translucent fill, submitted with the outline; the pose is already moved to the block. */
    @Inject(method = "submitBlockOutline", at = @At(value = "INVOKE", target = HIT_OUTLINE, ordinal = 1))
    private void medirian$fill(PoseStack poseStack, SubmitNodeCollector collector, LevelRenderState state, CallbackInfo ci) {
        final int argb = Hooks.blockOutlineFill();
        BlockOutlineRenderState outline = state.blockOutlineRenderState;
        if (argb == 0 || outline == null) {
            return;
        }
        final List<AABB> boxes = outline.shape().toAabbs();
        collector.submitCustomGeometry(poseStack, RenderTypes.debugQuads(), (pose, consumer) -> {
            for (AABB box : boxes) {
                AABB b = box.inflate(0.002);
                quad(consumer, pose, argb, b.minX, b.minY, b.minZ, b.maxX, b.minY, b.minZ, b.maxX, b.minY, b.maxZ, b.minX, b.minY, b.maxZ);
                quad(consumer, pose, argb, b.minX, b.maxY, b.minZ, b.minX, b.maxY, b.maxZ, b.maxX, b.maxY, b.maxZ, b.maxX, b.maxY, b.minZ);
                quad(consumer, pose, argb, b.minX, b.minY, b.minZ, b.minX, b.maxY, b.minZ, b.maxX, b.maxY, b.minZ, b.maxX, b.minY, b.minZ);
                quad(consumer, pose, argb, b.minX, b.minY, b.maxZ, b.maxX, b.minY, b.maxZ, b.maxX, b.maxY, b.maxZ, b.minX, b.maxY, b.maxZ);
                quad(consumer, pose, argb, b.minX, b.minY, b.minZ, b.minX, b.minY, b.maxZ, b.minX, b.maxY, b.maxZ, b.minX, b.maxY, b.minZ);
                quad(consumer, pose, argb, b.maxX, b.minY, b.minZ, b.maxX, b.maxY, b.minZ, b.maxX, b.maxY, b.maxZ, b.maxX, b.minY, b.maxZ);
            }
        });
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
