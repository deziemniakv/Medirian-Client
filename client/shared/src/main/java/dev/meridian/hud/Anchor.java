package dev.meridian.hud;

/**
 * Screen anchor of a HUD element. Positions are stored relative to an anchor so a layout survives
 * resolution and GUI scale changes: an element anchored bottom-right stays bottom-right.
 */
public enum Anchor {
    TOP_LEFT(0f, 0f), TOP_CENTER(0.5f, 0f), TOP_RIGHT(1f, 0f),
    MIDDLE_LEFT(0f, 0.5f), CENTER(0.5f, 0.5f), MIDDLE_RIGHT(1f, 0.5f),
    BOTTOM_LEFT(0f, 1f), BOTTOM_CENTER(0.5f, 1f), BOTTOM_RIGHT(1f, 1f);

    /** Horizontal factor: 0 = left, 0.5 = centre, 1 = right. */
    public final float fx;
    /** Vertical factor: 0 = top, 0.5 = middle, 1 = bottom. */
    public final float fy;

    Anchor(float fx, float fy) {
        this.fx = fx;
        this.fy = fy;
    }

    /** Picks the anchor of the screen third containing the element's centre. */
    public static Anchor fromPosition(float x, float y, float width, float height, float screenW, float screenH) {
        float cx = (x + width / 2f) / screenW;
        float cy = (y + height / 2f) / screenH;
        int col = cx < 1f / 3f ? 0 : (cx > 2f / 3f ? 2 : 1);
        int row = cy < 1f / 3f ? 0 : (cy > 2f / 3f ? 2 : 1);
        return values()[row * 3 + col];
    }

    /** Top-left X of an element of {@code width} anchored here with {@code offset}. */
    public float resolveX(float offset, float width, float screenW) {
        return fx * (screenW - width) + offset;
    }

    public float resolveY(float offset, float height, float screenH) {
        return fy * (screenH - height) + offset;
    }

    /** Inverse of {@link #resolveX}. */
    public float offsetX(float x, float width, float screenW) {
        return x - fx * (screenW - width);
    }

    public float offsetY(float y, float height, float screenH) {
        return y - fy * (screenH - height);
    }
}
