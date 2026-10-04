package dev.meridian.render;

import dev.meridian.platform.EffectView;
import dev.meridian.platform.EntityView;
import dev.meridian.platform.ItemView;

/**
 * 2D rendering backend implemented by every version adapter.
 *
 * <p>Coordinates are in Minecraft GUI units (scaled pixels) and are affected by the current
 * {@link #push()}/{@link #translate}/{@link #scale} transform. Colours are ARGB; a colour with
 * zero alpha is not drawn.
 *
 * <p>The primitive set is deliberately small: everything else (rounded panels, outlines, graphs,
 * widgets) is built in shared code on top of these calls, so both versions look identical.
 */
public interface Gfx {

    /** GUI width in GUI units. */
    int width();

    /** GUI height in GUI units. */
    int height();

    /** Real pixels per GUI unit (Minecraft's GUI scale factor). */
    double guiScale();

    /** Real pixels per unit of the current transform (guiScale × accumulated {@link #scale}). */
    double pixelScale();

    /** Filled rectangle between two corners (x2/y2 exclusive). */
    void fill(int x1, int y1, int x2, int y2, int argb);

    /** Vertical gradient. */
    void gradient(int x1, int y1, int x2, int y2, int topArgb, int bottomArgb);

    /** Draws text, honouring legacy {@code §} formatting codes. Returns the x after the text. */
    int text(String text, float x, float y, int argb, boolean shadow);

    int textWidth(String text);

    /** Line height of the font (9 in vanilla). */
    int fontHeight();

    void push();

    void pop();

    void translate(float x, float y);

    void scale(float x, float y);

    /** Clips subsequent drawing to the rectangle, given in the current transformed space. */
    void enableScissor(int x1, int y1, int x2, int y2);

    void disableScissor();

    /** Draws a 16×16 item icon with count/durability overlay disabled. */
    void item(ItemView item, int x, int y);

    /** Draws an 18×18 status effect icon. */
    void effectIcon(EffectView effect, int x, int y);

    /** Draws the face of a player/mob at {@code size}×{@code size}. Draws nothing when unavailable. */
    void entityHead(EntityView entity, int x, int y, int size);

    /** Draws a Meridian texture, {@code path} relative to {@code assets/meridian/textures/}. */
    void texture(String path, int x, int y, int width, int height, int argbTint);

    /**
     * Draws part of a Meridian texture stretched to {@code width}×{@code height}. The region is
     * given in fractions of the texture (0..1), so it does not depend on the texture's resolution.
     */
    void textureRegion(String path, int x, int y, int width, int height, float u0, float v0, float u1, float v1, int argbTint);

    /** Draws native rich text (e.g. scoreboard lines). */
    void richText(Object nativeText, float x, float y, int argb, boolean shadow);

    int richTextWidth(Object nativeText);
}
