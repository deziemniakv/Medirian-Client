package dev.meridian.mc1_21_11;

import dev.meridian.platform.EffectView;
import dev.meridian.platform.EntityView;
import dev.meridian.platform.ItemView;
import dev.meridian.render.Gfx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;

/**
 * {@link Gfx} backed by 1.21.11's {@link GuiGraphics}. One instance is reused for every frame;
 * {@link #begin} binds it to the current GuiGraphics. Tracks the accumulated scale so shared code
 * can draw at real-pixel resolution ({@link #pixelScale()}).
 */
public final class ModernGfx implements Gfx {

    private static final int MAX_DEPTH = 64;

    private final Minecraft minecraft;
    private final float[] scaleStack = new float[MAX_DEPTH];
    private GuiGraphics graphics;
    private Font font;
    private int depth;
    private float scale = 1f;

    public ModernGfx(Minecraft minecraft) {
        this.minecraft = minecraft;
    }

    public ModernGfx begin(GuiGraphics guiGraphics) {
        this.graphics = guiGraphics;
        this.font = minecraft.font;
        this.depth = 0;
        this.scale = 1f;
        return this;
    }

    @Override
    public int width() {
        return graphics.guiWidth();
    }

    @Override
    public int height() {
        return graphics.guiHeight();
    }

    @Override
    public double guiScale() {
        return minecraft.getWindow().getGuiScale();
    }

    @Override
    public double pixelScale() {
        return guiScale() * scale;
    }

    @Override
    public void fill(int x1, int y1, int x2, int y2, int argb) {
        if ((argb >>> 24) != 0 && x2 > x1 && y2 > y1) {
            graphics.fill(x1, y1, x2, y2, argb);
        }
    }

    @Override
    public void gradient(int x1, int y1, int x2, int y2, int topArgb, int bottomArgb) {
        if (((topArgb | bottomArgb) >>> 24) != 0) {
            graphics.fillGradient(x1, y1, x2, y2, topArgb, bottomArgb);
        }
    }

    @Override
    public int text(String text, float x, float y, int argb, boolean shadow) {
        if (text.isEmpty() || (argb >>> 24) == 0) {
            return (int) x;
        }
        int ix = (int) x;
        int iy = (int) y;
        if (ix == x && iy == y) {
            graphics.drawString(font, text, ix, iy, argb, shadow);
        } else {
            graphics.pose().pushMatrix();
            graphics.pose().translate(x, y);
            graphics.drawString(font, text, 0, 0, argb, shadow);
            graphics.pose().popMatrix();
        }
        return (int) x + font.width(text);
    }

    @Override
    public int textWidth(String text) {
        return font.width(text);
    }

    @Override
    public int fontHeight() {
        return font.lineHeight;
    }

    @Override
    public void push() {
        graphics.pose().pushMatrix();
        scaleStack[depth++] = scale;
    }

    @Override
    public void pop() {
        graphics.pose().popMatrix();
        scale = scaleStack[--depth];
    }

    @Override
    public void translate(float x, float y) {
        graphics.pose().translate(x, y);
    }

    @Override
    public void scale(float x, float y) {
        graphics.pose().scale(x, y);
        scale *= x;
    }

    @Override
    public void enableScissor(int x1, int y1, int x2, int y2) {
        graphics.enableScissor(x1, y1, x2, y2);
    }

    @Override
    public void disableScissor() {
        graphics.disableScissor();
    }

    @Override
    public void item(ItemView item, int x, int y) {
        Object handle = item.handle();
        if (handle instanceof ItemStack stack && !stack.isEmpty()) {
            graphics.renderItem(stack, x, y);
        }
    }

    @Override
    public void effectIcon(EffectView effect, int x, int y) {
        if (effect.handle() instanceof MobEffectInstance instance) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, Gui.getMobEffectSprite(instance.getEffect()), x, y, 18, 18);
        }
    }

    @Override
    public void entityHead(EntityView entity, int x, int y, int size) {
        if (entity.handle() instanceof AbstractClientPlayer player) {
            PlayerFaceRenderer.draw(graphics, player.getSkin(), x, y, size);
        }
    }

    @Override
    public void texture(String path, int x, int y, int width, int height, int argbTint) {
        Textures.Entry texture = Textures.get(minecraft, path);
        if (texture != null) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, texture.id(), x, y, 0f, 0f, width, height,
                    texture.width(), texture.height(), texture.width(), texture.height(), argbTint);
        }
    }

    @Override
    public void richText(Object nativeText, float x, float y, int argb, boolean shadow) {
        if (nativeText instanceof Component component) {
            graphics.pose().pushMatrix();
            graphics.pose().translate(x, y);
            graphics.drawString(font, component, 0, 0, argb, shadow);
            graphics.pose().popMatrix();
        }
    }

    @Override
    public int richTextWidth(Object nativeText) {
        return nativeText instanceof Component component ? font.width(component) : 0;
    }
}
