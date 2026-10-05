package dev.medirian.ui.widget;

import dev.medirian.input.Key;
import dev.medirian.render.Anim;
import dev.medirian.render.Gfx;

/**
 * Minimal retained UI component positioned by its owner. Coordinates are Medirian units.
 *
 * <p>A widget that takes keyboard focus or shows a popover (text field, key capture, colour
 * picker) becomes the screen's focused widget; a focused widget returning true from
 * {@link #captureClicks()} receives all mouse input and draws {@link #renderOverlay} on top.
 */
public abstract class Widget {

    public float x;
    public float y;
    public float w;
    public float h;
    protected final Anim hover = new Anim(0f, 18f);

    @SuppressWarnings("unchecked")
    public <W extends Widget> W bounds(float x, float y, float w, float h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        return (W) this;
    }

    public boolean contains(float mx, float my) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }

    /** Hover animation value 0..1 for the current frame. */
    protected float hoverValue(float mx, float my) {
        return hover.target(contains(mx, my) ? 1f : 0f).get();
    }

    public abstract void render(Gfx g, float mx, float my);

    /** Popover content drawn above the whole screen while focused. */
    public void renderOverlay(Gfx g, float mx, float my) {
    }

    public boolean mouseClicked(float mx, float my, int button) {
        return false;
    }

    public void mouseReleased(float mx, float my, int button) {
    }

    public void mouseDragged(float mx, float my) {
    }

    public boolean mouseScrolled(float mx, float my, double amount) {
        return false;
    }

    public boolean keyPressed(Key key, boolean ctrl, boolean shift) {
        return false;
    }

    public boolean charTyped(char c) {
        return false;
    }

    /** While focused: receive every mouse event, even outside the widget bounds. */
    public boolean captureClicks() {
        return false;
    }

    public void focusLost() {
    }
}
