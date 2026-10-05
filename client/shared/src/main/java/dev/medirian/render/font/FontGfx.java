package dev.medirian.render.font;

import dev.medirian.platform.EffectView;
import dev.medirian.platform.EntityView;
import dev.medirian.platform.ItemView;
import dev.medirian.render.Gfx;

/**
 * A {@link Gfx} that draws text with {@link MedirianFont} at the exact pixel size of the current
 * transform and passes everything else to the version's Gfx. Text the font cannot show (symbols
 * outside it) falls back to Minecraft's font.
 */
public final class FontGfx implements Gfx {

    /** Minecraft's colour codes §0-§f. */
    private static final int[] COLORS = {
        0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
        0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF
    };

    private final MedirianFont font;
    private final Gfx g;

    public FontGfx(MedirianFont font, Gfx delegate) {
        this.font = font;
        this.g = delegate;
    }

    public Gfx delegate() {
        return g;
    }

    @Override
    public int text(String text, float x, float y, int argb, boolean shadow) {
        if (text.isEmpty() || (argb >>> 24) < 4) {
            return (int) x;
        }
        if (!font.canDisplay(text)) {
            return g.text(text, x, y, argb, shadow);
        }
        double scale = g.pixelScale();
        int pixels = Math.max(6, (int) Math.round(MedirianFont.EM * scale));
        // rasterise first: new glyphs must be uploaded before anything is drawn with them
        prepare(text, pixels);
        upload();
        g.push();
        float inv = (float) (1.0 / scale);
        g.scale(inv, inv);
        float penX = (float) (x * scale);
        float baseline = (float) ((y + MedirianFont.BASELINE) * scale);
        if (shadow) {
            int offset = Math.max(1, (int) Math.round(scale * 0.5));
            draw(text, penX + offset, baseline + offset, argb, pixels, true);
        }
        draw(text, penX, baseline, argb, pixels, false);
        g.pop();
        return (int) (x + width(text));
    }

    private void prepare(String text, int pixels) {
        boolean bold = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '§' && i + 1 < text.length()) {
                char code = Character.toLowerCase(text.charAt(++i));
                bold = code == 'l' || (bold && code != 'r' && colorIndex(code) < 0);
                continue;
            }
            font.glyph(c, bold, pixels);
        }
    }

    private void upload() {
        for (GlyphAtlas.Page page : font.atlas().pages()) {
            if (page.dirty()) {
                g.uploadTexture(page.id, GlyphAtlas.SIZE, GlyphAtlas.SIZE, page.pixels);
                page.uploaded();
            }
        }
    }

    private void draw(String text, float penX, float baseline, int argb, int pixels, boolean shadowPass) {
        int alpha = argb & 0xFF000000;
        int color = shadowPass ? shadow(argb) : argb;
        boolean bold = false;
        float size = GlyphAtlas.SIZE;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '§' && i + 1 < text.length()) {
                char code = Character.toLowerCase(text.charAt(++i));
                int index = colorIndex(code);
                if (index >= 0) {
                    int rgb = alpha | COLORS[index];
                    color = shadowPass ? shadow(rgb) : rgb;
                    bold = false;
                } else if (code == 'l') {
                    bold = true;
                } else if (code == 'r') {
                    color = shadowPass ? shadow(argb) : argb;
                    bold = false;
                }
                continue;
            }
            MedirianFont.Glyph glyph = font.glyph(c, bold, pixels);
            if (glyph.page >= 0) {
                int gx = Math.round(penX) + glyph.offsetX;
                int gy = Math.round(baseline) + glyph.offsetY;
                g.dynamicTexture(font.atlas().pages().get(glyph.page).id, gx, gy, glyph.width, glyph.height,
                        glyph.x / size, glyph.y / size, (glyph.x + glyph.width) / size, (glyph.y + glyph.height) / size, color);
            }
            penX += glyph.advance;
        }
    }

    /** Minecraft's text shadow: the colour at a quarter of its brightness. */
    static int shadow(int argb) {
        return (argb & 0xFF000000) | ((argb & 0xFCFCFC) >> 2);
    }

    private static int colorIndex(char code) {
        if (code >= '0' && code <= '9') {
            return code - '0';
        }
        if (code >= 'a' && code <= 'f') {
            return code - 'a' + 10;
        }
        return -1;
    }

    /** Width in GUI units, ignoring § codes (bold text is wider). */
    public float width(String text) {
        float width = 0;
        boolean bold = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '§' && i + 1 < text.length()) {
                char code = Character.toLowerCase(text.charAt(++i));
                bold = code == 'l' || (bold && code != 'r' && colorIndex(code) < 0);
                continue;
            }
            width += font.width(c, bold);
        }
        return width;
    }

    @Override
    public int textWidth(String text) {
        return font.canDisplay(text) ? Math.round(width(text)) : g.textWidth(text);
    }

    @Override
    public int fontHeight() {
        return MedirianFont.LINE_HEIGHT;
    }

    // ------------------------------------------------------------------ everything else is the version's

    @Override
    public int width() {
        return g.width();
    }

    @Override
    public int height() {
        return g.height();
    }

    @Override
    public double guiScale() {
        return g.guiScale();
    }

    @Override
    public double pixelScale() {
        return g.pixelScale();
    }

    @Override
    public void fill(int x1, int y1, int x2, int y2, int argb) {
        g.fill(x1, y1, x2, y2, argb);
    }

    @Override
    public void gradient(int x1, int y1, int x2, int y2, int topArgb, int bottomArgb) {
        g.gradient(x1, y1, x2, y2, topArgb, bottomArgb);
    }

    @Override
    public void push() {
        g.push();
    }

    @Override
    public void pop() {
        g.pop();
    }

    @Override
    public void translate(float x, float y) {
        g.translate(x, y);
    }

    @Override
    public void scale(float x, float y) {
        g.scale(x, y);
    }

    @Override
    public void enableScissor(int x1, int y1, int x2, int y2) {
        g.enableScissor(x1, y1, x2, y2);
    }

    @Override
    public void disableScissor() {
        g.disableScissor();
    }

    @Override
    public void item(ItemView item, int x, int y) {
        g.item(item, x, y);
    }

    @Override
    public void effectIcon(EffectView effect, int x, int y) {
        g.effectIcon(effect, x, y);
    }

    @Override
    public void entityHead(EntityView entity, int x, int y, int size) {
        g.entityHead(entity, x, y, size);
    }

    @Override
    public void texture(String path, int x, int y, int width, int height, int argbTint) {
        g.texture(path, x, y, width, height, argbTint);
    }

    @Override
    public void textureRegion(String path, int x, int y, int width, int height, float u0, float v0, float u1, float v1, int argbTint) {
        g.textureRegion(path, x, y, width, height, u0, v0, u1, v1, argbTint);
    }

    @Override
    public void uploadTexture(String id, int width, int height, int[] argb) {
        g.uploadTexture(id, width, height, argb);
    }

    @Override
    public void dynamicTexture(String id, int x, int y, int width, int height, float u0, float v0, float u1, float v1, int argbTint) {
        g.dynamicTexture(id, x, y, width, height, u0, v0, u1, v1, argbTint);
    }

    @Override
    public void richText(Object nativeText, float x, float y, int argb, boolean shadow) {
        g.richText(nativeText, x, y, argb, shadow);
    }

    @Override
    public int richTextWidth(Object nativeText) {
        return g.richTextWidth(nativeText);
    }
}
