package dev.medirian.mc1_8_9.mixin;

import com.mojang.blaze3d.platform.GlStateManager;
import dev.medirian.platform.Hooks;
import dev.medirian.waypoint.Waypoint;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.CameraView;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Block Overlay (outline colour/width and an optional translucent fill of the selected block) and
 * waypoint beams.
 */
@Mixin(WorldRenderer.class)
public abstract class WorldRendererMixin {

    @Unique private static final double BEAM_HALF_WIDTH = 0.12;
    @Unique private static final double BEAM_HEIGHT = 256;

    /**
     * Waypoint beams after the entities, in the same space: positions relative to the view entity's
     * interpolated position (BlockEntityRenderDispatcher.CAMERA_* holds it for this frame).
     */
    @Inject(method = "renderEntities", at = @At("TAIL"))
    private void medirian$beams(Entity cameraEntity, CameraView view, float tickDelta, CallbackInfo ci) {
        List<Waypoint> beams = Hooks.waypointBeams();
        if (beams.isEmpty()) {
            return;
        }
        double ox = BlockEntityRenderDispatcher.CAMERA_X;
        double oy = BlockEntityRenderDispatcher.CAMERA_Y;
        double oz = BlockEntityRenderDispatcher.CAMERA_Z;
        // entity rendering leaves item lighting on, which would darken the unlit beam colours
        DiffuseLighting.disable();
        GlStateManager.disableTexture();
        GlStateManager.enableBlend();
        GlStateManager.blendFuncSeparate(770, 771, 1, 0);
        GlStateManager.depthMask(false);
        GlStateManager.disableCull();
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_QUADS, VertexFormats.POSITION_COLOR);
        double w = BEAM_HALF_WIDTH;
        for (Waypoint waypoint : beams) {
            int r = (waypoint.color >> 16) & 0xFF;
            int g = (waypoint.color >> 8) & 0xFF;
            int b = waypoint.color & 0xFF;
            double cx = waypoint.x + 0.5 - ox;
            double cz = waypoint.z + 0.5 - oz;
            double y0 = waypoint.y - oy;
            double y1 = y0 + BEAM_HEIGHT;
            medirian$beamSide(buffer, r, g, b, cx - w, cz - w, cx + w, cz - w, y0, y1);
            medirian$beamSide(buffer, r, g, b, cx + w, cz - w, cx + w, cz + w, y0, y1);
            medirian$beamSide(buffer, r, g, b, cx + w, cz + w, cx - w, cz + w, y0, y1);
            medirian$beamSide(buffer, r, g, b, cx - w, cz + w, cx - w, cz - w, y0, y1);
        }
        tessellator.draw();
        GlStateManager.enableCull();
        GlStateManager.depthMask(true);
        GlStateManager.disableBlend();
        GlStateManager.enableTexture();
        DiffuseLighting.enableNormally();
    }

    @Unique
    private static void medirian$beamSide(BufferBuilder buffer, int r, int g, int b,
                                          double x1, double z1, double x2, double z2, double y0, double y1) {
        buffer.vertex(x1, y0, z1).color(r, g, b, 0x66).next();
        buffer.vertex(x2, y0, z2).color(r, g, b, 0x66).next();
        buffer.vertex(x2, y1, z2).color(r, g, b, 0x66).next();
        buffer.vertex(x1, y1, z1).color(r, g, b, 0x66).next();
    }

    @Unique
    private static int medirian$outline;

    @Redirect(method = "drawBlockOutline", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/GlStateManager;color(FFFF)V"))
    private void medirian$color(float r, float g, float b, float a) {
        int vanilla = (Math.round(a * 255) << 24) | (Math.round(r * 255) << 16) | (Math.round(g * 255) << 8) | Math.round(b * 255);
        medirian$outline = Hooks.blockOutlineColor(vanilla);
        medirian$apply(medirian$outline);
    }

    @Redirect(method = "drawBlockOutline", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glLineWidth(F)V", remap = false))
    private void medirian$width(float width) {
        GL11.glLineWidth(Hooks.blockOutlineWidth(width));
    }

    @Redirect(method = "drawBlockOutline", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/WorldRenderer;drawBox(Lnet/minecraft/util/math/Box;)V"))
    private void medirian$box(Box box) {
        int fill = Hooks.blockOutlineFill();
        if (fill != 0) {
            medirian$apply(fill);
            GlStateManager.disableCull();
            Tessellator tessellator = Tessellator.getInstance();
            BufferBuilder buffer = tessellator.getBuffer();
            buffer.begin(GL11.GL_QUADS, VertexFormats.POSITION);
            medirian$quad(buffer, box.minX, box.minY, box.minZ, box.maxX, box.minY, box.minZ, box.maxX, box.minY, box.maxZ, box.minX, box.minY, box.maxZ);
            medirian$quad(buffer, box.minX, box.maxY, box.minZ, box.minX, box.maxY, box.maxZ, box.maxX, box.maxY, box.maxZ, box.maxX, box.maxY, box.minZ);
            medirian$quad(buffer, box.minX, box.minY, box.minZ, box.minX, box.maxY, box.minZ, box.maxX, box.maxY, box.minZ, box.maxX, box.minY, box.minZ);
            medirian$quad(buffer, box.minX, box.minY, box.maxZ, box.maxX, box.minY, box.maxZ, box.maxX, box.maxY, box.maxZ, box.minX, box.maxY, box.maxZ);
            medirian$quad(buffer, box.minX, box.minY, box.minZ, box.minX, box.minY, box.maxZ, box.minX, box.maxY, box.maxZ, box.minX, box.maxY, box.minZ);
            medirian$quad(buffer, box.maxX, box.minY, box.minZ, box.maxX, box.maxY, box.minZ, box.maxX, box.maxY, box.maxZ, box.maxX, box.minY, box.maxZ);
            tessellator.draw();
            GlStateManager.enableCull();
            medirian$apply(medirian$outline);
        }
        WorldRenderer.drawBox(box);
    }

    @Unique
    private static void medirian$apply(int argb) {
        GlStateManager.color(((argb >> 16) & 0xFF) / 255f, ((argb >> 8) & 0xFF) / 255f, (argb & 0xFF) / 255f, (argb >>> 24) / 255f);
    }

    @Unique
    private static void medirian$quad(BufferBuilder buffer, double x1, double y1, double z1, double x2, double y2, double z2,
                                      double x3, double y3, double z3, double x4, double y4, double z4) {
        buffer.vertex(x1, y1, z1).next();
        buffer.vertex(x2, y2, z2).next();
        buffer.vertex(x3, y3, z3).next();
        buffer.vertex(x4, y4, z4).next();
    }
}
