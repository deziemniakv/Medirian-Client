package dev.meridian.ui.widget;

import dev.meridian.render.Gfx;

/** Several widgets laid out horizontally, right-aligned within the given bounds. */
public final class WidgetRow extends Widget {

    private final Widget[] children;
    private final float[] widths;
    private static final float GAP = 4;

    public WidgetRow(Widget[] children, float[] widths) {
        this.children = children;
        this.widths = widths;
        this.h = 14;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <W extends Widget> W bounds(float x, float y, float w, float h) {
        super.bounds(x, y, w, h);
        float cx = x + w;
        for (int i = children.length - 1; i >= 0; i--) {
            cx -= widths[i];
            children[i].bounds(cx, y, widths[i], h);
            cx -= GAP;
        }
        return (W) this;
    }

    /** Total width needed for the children. */
    public float preferredWidth() {
        float total = 0;
        for (float width : widths) {
            total += width;
        }
        return total + GAP * (widths.length - 1);
    }

    @Override
    public void render(Gfx g, float mx, float my) {
        for (Widget child : children) {
            child.render(g, mx, my);
        }
    }

    @Override
    public boolean mouseClicked(float mx, float my, int button) {
        for (Widget child : children) {
            if (child.mouseClicked(mx, my, button)) {
                return true;
            }
        }
        return false;
    }
}
