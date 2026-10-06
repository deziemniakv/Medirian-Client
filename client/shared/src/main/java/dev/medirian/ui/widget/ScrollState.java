package dev.medirian.ui.widget;

import dev.medirian.render.Anim;
import dev.medirian.render.Colors;
import dev.medirian.render.Gfx;
import dev.medirian.render.Theme;

/** Smooth vertical scrolling state with a thin pixel scrollbar. */
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
        Theme t = Theme.current();
        g.fill(Math.round(x), Math.round(y), Math.round(x) + 2, Math.round(y + view), Colors.withAlpha(t.panelDark, 0xC0));
        g.fill(Math.round(x), Math.round(barY), Math.round(x) + 2, Math.round(barY + barHeight), t.borderStrong);
        g.fill(Math.round(x), Math.round(barY), Math.round(x) + 1, Math.round(barY + barHeight), t.textMuted);
    }
}
