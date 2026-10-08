package dev.medirian.mc26_3;

import dev.medirian.platform.EffectView;
import dev.medirian.platform.EntityView;
import dev.medirian.platform.ItemView;
import dev.medirian.render.Gfx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;

/**
 * {@link Gfx} backed by 26.3's {@link GuiGraphicsExtractor}. One instance is reused for every frame;
 * {@link #begin} binds it to the current GuiGraphicsExtractor. Tracks the accumulated scale so shared code
 * can draw at real-pixel resolution ({@link #pixelScale()}).
 */
public final class ModernGfx implements Gfx {

    private static final int MAX_DEPTH = 64;

    private final Minecraft minecraft;
    /** Textures made at runtime (font atlas pages), by id. */
    private final java.util.Map<String, net.minecraft.client.renderer.texture.DynamicTexture> dynamic = new java.util.HashMap<>();
    private final float[] scaleStack = new float[MAX_DEPTH];
    private GuiGraphicsExtractor graphics;
    private Font font;
    private int depth;
    private float scale = 1f;
    private final ModernBackdrop backdrop;

    public ModernGfx(Minecraft minecraft) {
        this.minecraft = minecraft;
        this.backdrop = new ModernBackdrop(minecraft);
    }

    public ModernGfx begin(GuiGraphicsExtractor guiGraphics) {
        this.graphics = guiGraphics;
        this.font = minecraft.font;
        this.depth = 0;
        this.scale = 1f;
        this.backdrop.ready = false;
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
            graphics.text(font, text, ix, iy, argb, shadow);
        } else {
            graphics.pose().pushMatrix();
            graphics.pose().translate(x, y);
            graphics.text(font, text, 0, 0, argb, shadow);
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
            graphics.item(stack, x, y);
        }
    }

    @Override
    public void effectIcon(EffectView effect, int x, int y) {
        if (effect.handle() instanceof MobEffectInstance instance) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, net.minecraft.client.gui.Hud.getMobEffectSprite(instance.getEffect()), x, y, 18, 18);
        }
    }

    @Override
    public void entityHead(EntityView entity, int x, int y, int size) {
        if (entity.handle() instanceof AbstractClientPlayer player) {
            PlayerFaceExtractor.extractRenderState(graphics, player.getSkin(), x, y, size);
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
    public void textureRegion(String path, int x, int y, int width, int height, float u0, float v0, float u1, float v1, int argbTint) {
        Textures.Entry texture = Textures.get(minecraft, path);
        if (texture != null) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, texture.id(), x, y, u0 * texture.width(), v0 * texture.height(), width, height,
                    Math.round((u1 - u0) * texture.width()), Math.round((v1 - v0) * texture.height()),
                    texture.width(), texture.height(), argbTint);
        }
    }

    @Override
    public void uploadTexture(String id, int width, int height, int[] argb) {
        net.minecraft.client.renderer.texture.DynamicTexture texture = dynamic.get(id);
        if (texture == null || texture.getPixels().getWidth() != width || texture.getPixels().getHeight() != height) {
            texture = new net.minecraft.client.renderer.texture.DynamicTexture(() -> "medirian:" + id, width, height, false);
            minecraft.getTextureManager().register(dynamicId(id), texture);
            dynamic.put(id, texture);
        }
        com.mojang.blaze3d.platform.NativeImage image = texture.getPixels();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setPixel(x, y, argb[y * width + x]);
            }
        }
        texture.upload();
    }

    private static net.minecraft.resources.Identifier dynamicId(String id) {
        return net.minecraft.resources.Identifier.fromNamespaceAndPath("medirian", "runtime/" + id);
    }

    @Override
    public void dynamicTexture(String id, int x, int y, int width, int height, float u0, float v0, float u1, float v1, int argbTint) {
        net.minecraft.client.renderer.texture.DynamicTexture texture = dynamic.get(id);
        if (texture == null) {
            return;
        }
        int texWidth = texture.getPixels().getWidth();
        int texHeight = texture.getPixels().getHeight();
        graphics.blit(RenderPipelines.GUI_TEXTURED, dynamicId(id), x, y, u0 * texWidth, v0 * texHeight, width, height,
                Math.round((u1 - u0) * texWidth), Math.round((v1 - v0) * texHeight), texWidth, texHeight, argbTint);
    }

    @Override
    public void prepareBackdrop(int strength) {
        backdrop.request(strength);
    }

    /** Makes the backdrop requested while the HUD was collected; the world is drawn by now (GameRendererMixin). */
    public void renderBackdrop() {
        backdrop.render();
    }

    @Override
    public boolean backdropReady() {
        return backdrop.ready;
    }

    @Override
    public void backdrop(int x1, int y1, int x2, int y2) {
        if (!backdrop.ready || x2 <= x1 || y2 <= y1) {
            return;
        }
        // where the rectangle is on screen, in pixels of the frame
        org.joml.Vector2f a = graphics.pose().transformPosition(x1, y1, new org.joml.Vector2f());
        org.joml.Vector2f b = graphics.pose().transformPosition(x2, y2, new org.joml.Vector2f());
        double gui = guiScale();
        float sx = (float) (a.x * gui);
        float sy = (float) (a.y * gui);
        int sw = Math.round((float) (b.x * gui) - sx);
        int sh = Math.round((float) (b.y * gui) - sy);
        int fw = backdrop.frameWidth;
        int fh = backdrop.frameHeight;
        // the frame is stored bottom-up: flip the region vertically; drawn opaque, the tint goes on top
        graphics.blit(RenderPipelines.GUI_OPAQUE_TEXTURED_BACKGROUND, ModernBackdrop.ID, x1, y1, sx, fh - sy,
                x2 - x1, y2 - y1, sw, -sh, fw, fh, 0xFFFFFFFF);
    }

    @Override
    public void richText(Object nativeText, float x, float y, int argb, boolean shadow) {
        if (nativeText instanceof Component component) {
            graphics.pose().pushMatrix();
            graphics.pose().translate(x, y);
            graphics.text(font, component, 0, 0, argb, shadow);
            graphics.pose().popMatrix();
        }
    }

    @Override
    public int richTextWidth(Object nativeText) {
        return nativeText instanceof Component component ? font.width(component) : 0;
    }
}
