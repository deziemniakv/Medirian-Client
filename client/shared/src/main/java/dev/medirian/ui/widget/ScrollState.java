package dev.medirian.ui.widget;

import dev.medirian.render.Anim;
import dev.medirian.render.Colors;
import dev.medirian.render.Gfx;
import dev.medirian.render.Theme;
import dev.medirian.render.UiDraw;

/** Smooth vertical scrolling state with a thin scrollbar. */
public final class ScrollState {

    private final Anim anim = new Anim(0f, 20f);
    private float target;
    private float content;
    private float view;

    public void setBounds(float contentHeight, float viewHeight) {
        this.content = contentHeight;
        this.view = viewHeight;
        target = clamp(target);
    }

    public void scroll(double amount) {
        target = clamp(target - (float) amount * 28f);
    }

    public void reset() {
        target = 0;
        anim.snap(0);
    }

    public float offset() {
        return anim.target(target).get();
    }

    private float clamp(float value) {
        return Math.max(0f, Math.min(Math.max(0f, content - view), value));
    }

    public void renderBar(Gfx g, float x, float y) {
        if (content <= view + 0.5f) {
            return;
        }
        float barHeight = Math.max(16f, view * view / content);
        float barY = y + (view - barHeight) * (offset() / (content - view));
        UiDraw.roundRect(g, x, barY, 2, barHeight, 1, Colors.withAlpha(Theme.current().textMuted, 0x90));
    }
}
