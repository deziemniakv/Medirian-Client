package dev.meridian.mc1_8_9;

import com.mojang.blaze3d.platform.GlStateManager;
import dev.meridian.platform.EffectView;
import dev.meridian.platform.EntityView;
import dev.meridian.platform.ItemView;
import dev.meridian.render.Gfx;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.Window;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import org.lwjgl.opengl.GL11;

/**
 * {@link Gfx} for 1.8.9 on fixed-function OpenGL ({@link GlStateManager} + {@link Tessellator}).
 *
 * <p>Fills are drawn with our own quad code (vanilla {@code DrawableHelper.fill} disables blending
 * afterwards, which would make following translucent text opaque). The 2D transform is mirrored
 * on the CPU so scissor rectangles and {@link #pixelScale()} can be computed.
 */
public final class LegacyGfx implements Gfx {

    private static final Identifier INVENTORY = new Identifier("textures/gui/container/inventory.png");
    private static final int MAX_DEPTH = 64;

    /** Access to DrawableHelper's protected helpers. */
    private static final class Helper extends DrawableHelper {
        void gradient(int x1, int y1, int x2, int y2, int top, int bottom) {
            fillGradient(x1, y1, x2, y2, top, bottom);
        }
    }

    private final Helper helper = new Helper();
    private final MinecraftClient client;
    private final float[] stack = new float[MAX_DEPTH * 3];
    private int depth;
    private float tx;
    private float ty;
    private float scale = 1f;
    private int width;
    private int height;
    private int guiScale = 1;
    private TextRenderer font;

    public LegacyGfx(MinecraftClient client) {
        this.client = client;
    }

    /** Prepares a frame: GUI size from vanilla's scaled resolution and a clean 2D state. */
    public LegacyGfx begin() {
        Window window = new Window(client);
        width = window.getWidth();
        height = window.getHeight();
        guiScale = window.getScaleFactor();
        font = client.textRenderer;
        depth = 0;
        tx = 0;
        ty = 0;
        scale = 1f;
        GlStateManager.enableBlend();
        GlStateManager.blendFuncSeparate(770, 771, 1, 0);
        GlStateManager.disableLighting();
        GlStateManager.color(1f, 1f, 1f, 1f);
        return this;
    }

    /** Restores the state vanilla expects after our drawing. */
    public void end() {
        GlStateManager.color(1f, 1f, 1f, 1f);
        GlStateManager.enableTexture();
    }

    @Override
    public int width() {
        return width;
    }

    @Override
    public int height() {
        return height;
    }

    @Override
    public double guiScale() {
        return guiScale;
    }

    @Override
    public double pixelScale() {
        return guiScale * scale;
    }

    @Override
    public void fill(int x1, int y1, int x2, int y2, int argb) {
        int alpha = argb >>> 24;
        if (alpha == 0 || x2 <= x1 || y2 <= y1) {
            return;
        }
        GlStateManager.enableBlend();
        GlStateManager.disableTexture();
        GlStateManager.blendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color(((argb >> 16) & 0xFF) / 255f, ((argb >> 8) & 0xFF) / 255f, (argb & 0xFF) / 255f, alpha / 255f);
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_QUADS, VertexFormats.POSITION);
        buffer.vertex(x1, y2, 0).next();
        buffer.vertex(x2, y2, 0).next();
        buffer.vertex(x2, y1, 0).next();
        buffer.vertex(x1, y1, 0).next();
        tessellator.draw();
        GlStateManager.enableTexture();
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    @Override
    public void gradient(int x1, int y1, int x2, int y2, int topArgb, int bottomArgb) {
        if (((topArgb | bottomArgb) >>> 24) == 0) {
            return;
        }
        GlStateManager.disableAlphaTest();
        helper.gradient(x1, y1, x2, y2, topArgb, bottomArgb);
        GlStateManager.enableBlend();
        GlStateManager.blendFuncSeparate(770, 771, 1, 0);
    }

    @Override
    public int text(String text, float x, float y, int argb, boolean shadow) {
        if (text.isEmpty() || (argb >>> 24) < 4) {
            return (int) x;
        }
        GlStateManager.enableBlend();
        return font.draw(text, x, y, argb, shadow);
    }

    @Override
    public int textWidth(String text) {
        return font.getStringWidth(text);
    }

    @Override
    public int fontHeight() {
        return font.fontHeight;
    }

    @Override
    public void push() {
        GlStateManager.pushMatrix();
        int i = depth++ * 3;
        stack[i] = tx;
        stack[i + 1] = ty;
        stack[i + 2] = scale;
    }

    @Override
    public void pop() {
        GlStateManager.popMatrix();
        int i = --depth * 3;
        tx = stack[i];
        ty = stack[i + 1];
        scale = stack[i + 2];
    }

    @Override
    public void translate(float x, float y) {
        GlStateManager.translate(x, y, 0f);
        tx += x * scale;
        ty += y * scale;
    }

    @Override
    public void scale(float x, float y) {
        GlStateManager.scale(x, y, 1f);
        scale *= x;
    }

    @Override
    public void enableScissor(int x1, int y1, int x2, int y2) {
        // transform to GUI space, then to window pixels (OpenGL's origin is bottom-left)
        double gx1 = tx + x1 * scale;
        double gy1 = ty + y1 * scale;
        double gx2 = tx + x2 * scale;
        double gy2 = ty + y2 * scale;
        int px = (int) Math.floor(gx1 * guiScale);
        int pw = (int) Math.ceil((gx2 - gx1) * guiScale);
        int ph = (int) Math.ceil((gy2 - gy1) * guiScale);
        int py = client.height - (int) Math.ceil(gy2 * guiScale);
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(px, py, Math.max(0, pw), Math.max(0, ph));
    }

    @Override
    public void disableScissor() {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    @Override
    public void item(ItemView item, int x, int y) {
        Object handle = item.handle();
        if (!(handle instanceof ItemStack)) {
            return;
        }
        GlStateManager.enableRescaleNormal();
        DiffuseLighting.enable();
        client.getItemRenderer().renderInGuiWithOverrides((ItemStack) handle, x, y);
        DiffuseLighting.disable();
        GlStateManager.disableRescaleNormal();
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.blendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    @Override
    public void effectIcon(EffectView effect, int x, int y) {
        Object handle = effect.handle();
        if (!(handle instanceof StatusEffectInstance)) {
            return;
        }
        StatusEffect type = StatusEffect.STATUS_EFFECTS[((StatusEffectInstance) handle).getEffectId()];
        if (type == null || !type.hasIcon()) {
            return;
        }
        int index = type.getIconLevel();
        GlStateManager.color(1f, 1f, 1f, 1f);
        client.getTextureManager().bindTexture(INVENTORY);
        helper.drawTexture(x, y, index % 8 * 18, 198 + index / 8 * 18, 18, 18);
    }

    @Override
    public void entityHead(EntityView entity, int x, int y, int size) {
        Object handle = entity.handle();
        if (!(handle instanceof AbstractClientPlayerEntity)) {
            return;
        }
        GlStateManager.color(1f, 1f, 1f, 1f);
        client.getTextureManager().bindTexture(((AbstractClientPlayerEntity) handle).getSkinId());
        DrawableHelper.drawTexture(x, y, 8f, 8f, 8, 8, size, size, 64f, 64f);
        DrawableHelper.drawTexture(x, y, 40f, 8f, 8, 8, size, size, 64f, 64f);
    }

    @Override
    public void texture(String path, int x, int y, int w, int h, int argbTint) {
        Textures.Entry entry = Textures.get(client, path);
        if (entry == null) {
            return;
        }
        GlStateManager.enableBlend();
        GlStateManager.color(((argbTint >> 16) & 0xFF) / 255f, ((argbTint >> 8) & 0xFF) / 255f, (argbTint & 0xFF) / 255f,
                (argbTint >>> 24) / 255f);
        client.getTextureManager().bindTexture(entry.id);
        DrawableHelper.drawTexture(x, y, 0f, 0f, entry.width, entry.height, w, h, entry.width, entry.height);
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    @Override
    public void richText(Object nativeText, float x, float y, int argb, boolean shadow) {
        if (nativeText instanceof String) {
            text((String) nativeText, x, y, argb, shadow);
        }
    }

    @Override
    public int richTextWidth(Object nativeText) {
        return nativeText instanceof String ? font.getStringWidth((String) nativeText) : 0;
    }
}
