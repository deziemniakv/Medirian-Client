package dev.medirian.render.font;

import dev.medirian.core.Log;

import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Medirian's UI font (Lato, SIL Open Font License) rasterised on demand at the exact pixel size
 * it is shown at, so text stays sharp at every GUI scale. Glyphs are cached per size in a
 * {@link GlyphAtlas}.
 *
 * <p>Layout works in GUI units like Minecraft's font: a line is {@link #LINE_HEIGHT} units high
 * and widths do not depend on the scale text is drawn at.
 */
public final class MedirianFont {

    /** Font size (em) in GUI units: capitals are about as tall as Minecraft's (7 units). */
    public static final float EM = 9.6f;
    /** Baseline below the top of a line, in GUI units. */
    public static final float BASELINE = 7.4f;
    public static final int LINE_HEIGHT = 9;
    /** Pixel size the unit widths are measured at. */
    private static final int REFERENCE = 96;

    /** A rasterised glyph at one pixel size. */
    public static final class Glyph {
        /** Atlas page, or -1 for glyphs without pixels (spaces). */
        public final int page;
        public final int x;
        public final int y;
        public final int width;
        public final int height;
        /** Bitmap position relative to the pen on the baseline, in pixels. */
        public final int offsetX;
        public final int offsetY;
        public final float advance;

        Glyph(int page, int x, int y, int width, int height, int offsetX, int offsetY, float advance) {
            this.page = page;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.advance = advance;
        }
    }

    private final Font regular;
    private final Font bold;
    private final FontRenderContext context = new FontRenderContext(null, true, true);
    private final GlyphAtlas atlas = new GlyphAtlas("medirian_font_");
    private final Map<Long, Glyph> glyphs = new HashMap<Long, Glyph>();
    private final Map<Long, Font> sized = new HashMap<Long, Font>();
    private final Map<Integer, Float> widths = new HashMap<Integer, Float>();

    private MedirianFont(Font regular, Font bold) {
        this.regular = regular;
        this.bold = bold;
    }

    /** Loads the bundled font, or returns null when the runtime cannot (no AWT fonts available). */
    public static MedirianFont load() {
        try {
            return new MedirianFont(read("Lato-Regular.ttf"), read("Lato-Bold.ttf"));
        } catch (Throwable t) {
            Log.warn("Medirian font unavailable, using Minecraft's font: {}", t.toString());
            return null;
        }
    }

    private static Font read(String name) throws Exception {
        InputStream in = MedirianFont.class.getResourceAsStream("/assets/medirian/fonts/" + name);
        if (in == null) {
            throw new IllegalStateException(name + " missing");
        }
        try {
            return Font.createFont(Font.TRUETYPE_FONT, in);
        } finally {
            in.close();
        }
    }

    public GlyphAtlas atlas() {
        return atlas;
    }

    /** Whether every character of {@code text} (ignoring § codes) exists in the font. */
    public boolean canDisplay(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '§') {
                i++;
                continue;
            }
            if (!regular.canDisplay(c)) {
                return false;
            }
        }
        return true;
    }

    private Font font(boolean isBold, int pixels) {
        long key = ((long) pixels << 1) | (isBold ? 1 : 0);
        Font font = sized.get(key);
        if (font == null) {
            font = (isBold ? bold : regular).deriveFont((float) pixels);
            sized.put(key, font);
        }
        return font;
    }

    /** Width of a character in GUI units (independent of the drawing scale). */
    public float width(char c, boolean isBold) {
        int key = (c << 1) | (isBold ? 1 : 0);
        Float width = widths.get(key);
        if (width == null) {
            GlyphVector vector = font(isBold, REFERENCE).createGlyphVector(context, String.valueOf(c));
            width = vector.getGlyphMetrics(0).getAdvance() / REFERENCE * EM;
            widths.put(key, width);
        }
        return width;
    }

    /** The glyph of {@code c} at {@code pixels} pixels per em, rasterised on first use. */
    public Glyph glyph(char c, boolean isBold, int pixels) {
        long key = ((long) pixels << 17) | ((long) c << 1) | (isBold ? 1 : 0);
        Glyph glyph = glyphs.get(key);
        if (glyph == null) {
            glyph = rasterise(c, isBold, pixels);
            glyphs.put(key, glyph);
        }
        return glyph;
    }

    private Glyph rasterise(char c, boolean isBold, int pixels) {
        Font font = font(isBold, pixels);
        GlyphVector vector = font.createGlyphVector(context, String.valueOf(c));
        float advance = vector.getGlyphMetrics(0).getAdvance();
        Rectangle bounds = vector.getPixelBounds(context, 0, 0);
        if (bounds.width <= 0 || bounds.height <= 0) {
            return new Glyph(-1, 0, 0, 0, 0, 0, 0, advance);
        }
        int w = bounds.width + 2;
        int h = bounds.height + 2;
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setColor(java.awt.Color.WHITE);
        g.drawGlyphVector(vector, 1 - bounds.x, 1 - bounds.y);
        g.dispose();
        int[] alpha = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                alpha[y * w + x] = image.getRGB(x, y) >>> 24;
            }
        }
        int[] spot = atlas.add(alpha, w, h);
        if (spot == null) {
            return new Glyph(-1, 0, 0, 0, 0, 0, 0, advance);
        }
        return new Glyph(spot[0], spot[1], spot[2], w, h, bounds.x - 1, bounds.y - 1, advance);
    }
}
