package dev.meridian.mc1_8_9.mixin;

import com.mojang.blaze3d.platform.GlStateManager;
import dev.meridian.platform.Hooks;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.util.math.Box;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Block Overlay: outline colour/width and an optional translucent fill of the selected block. */
@Mixin(WorldRenderer.class)
public abstract class WorldRendererMixin {

    @Unique
    private static int meridian$outline;

    @Redirect(method = "drawBlockOutline", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/GlStateManager;color(FFFF)V"))
    private void meridian$color(float r, float g, float b, float a) {
        int vanilla = (Math.round(a * 255) << 24) | (Math.round(r * 255) << 16) | (Math.round(g * 255) << 8) | Math.round(b * 255);
        meridian$outline = Hooks.blockOutlineColor(vanilla);
        meridian$apply(meridian$outline);
    }

    @Redirect(method = "drawBlockOutline", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glLineWidth(F)V", remap = false))
    private void meridian$width(float width) {
        GL11.glLineWidth(Hooks.blockOutlineWidth(width));
    }

    @Redirect(method = "drawBlockOutline", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/WorldRenderer;drawBox(Lnet/minecraft/util/math/Box;)V"))
    private void meridian$box(Box box) {
        int fill = Hooks.blockOutlineFill();
        if (fill != 0) {
            meridian$apply(fill);
            GlStateManager.disableCull();
            Tessellator tessellator = Tessellator.getInstance();
            BufferBuilder buffer = tessellator.getBuffer();
            buffer.begin(GL11.GL_QUADS, VertexFormats.POSITION);
            meridian$quad(buffer, box.minX, box.minY, box.minZ, box.maxX, box.minY, box.minZ, box.maxX, box.minY, box.maxZ, box.minX, box.minY, box.maxZ);
            meridian$quad(buffer, box.minX, box.maxY, box.minZ, box.minX, box.maxY, box.maxZ, box.maxX, box.maxY, box.maxZ, box.maxX, box.maxY, box.minZ);
            meridian$quad(buffer, box.minX, box.minY, box.minZ, box.minX, box.maxY, box.minZ, box.maxX, box.maxY, box.minZ, box.maxX, box.minY, box.minZ);
            meridian$quad(buffer, box.minX, box.minY, box.maxZ, box.maxX, box.minY, box.maxZ, box.maxX, box.maxY, box.maxZ, box.minX, box.maxY, box.maxZ);
            meridian$quad(buffer, box.minX, box.minY, box.minZ, box.minX, box.minY, box.maxZ, box.minX, box.maxY, box.maxZ, box.minX, box.maxY, box.minZ);
            meridian$quad(buffer, box.maxX, box.minY, box.minZ, box.maxX, box.maxY, box.minZ, box.maxX, box.maxY, box.maxZ, box.maxX, box.minY, box.maxZ);
            tessellator.draw();
            GlStateManager.enableCull();
            meridian$apply(meridian$outline);
        }
        WorldRenderer.drawBox(box);
    }

    @Unique
    private static void meridian$apply(int argb) {
        GlStateManager.color(((argb >> 16) & 0xFF) / 255f, ((argb >> 8) & 0xFF) / 255f, (argb & 0xFF) / 255f, (argb >>> 24) / 255f);
    }

    @Unique
    private static void meridian$quad(BufferBuilder buffer, double x1, double y1, double z1, double x2, double y2, double z2,
                                      double x3, double y3, double z3, double x4, double y4, double z4) {
        buffer.vertex(x1, y1, z1).next();
        buffer.vertex(x2, y2, z2).next();
        buffer.vertex(x3, y3, z3).next();
        buffer.vertex(x4, y4, z4).next();
    }
}
