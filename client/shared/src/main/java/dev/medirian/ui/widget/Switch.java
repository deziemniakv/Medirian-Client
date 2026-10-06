package dev.medirian.ui.widget;

import dev.medirian.render.Anim;
import dev.medirian.render.Gfx;
import dev.medirian.render.Pixel;
import dev.medirian.render.Theme;

/** Pixel lever: a sunken track with a raised knob; the track glows pumpkin when on (24×12). */
public final class Switch extends Widget {

    /** Reads the current state. */
    public interface State {
        boolean get();
    }

    /** Applies a new state. */
    public interface Toggle {
        void set(boolean on);
    }

    private final State state;
    private final Toggle toggle;
    private final Anim knob;

    public Switch(State state, Toggle toggle) {
        this.state = state;
        this.toggle = toggle;
        this.knob = new Anim(state.get() ? 1f : 0f, 22f);
        this.w = 24;
        this.h = 12;
    }

    @Override
    public void render(Gfx g, float mx, float my) {
        Theme t = Theme.current();
        boolean on = state.get();
        float p = knob.target(on ? 1f : 0f).get();
        float hov = hoverValue(mx, my);
        int x0 = Math.round(x);
        int y0 = Math.round(y);
        int w0 = Math.round(w);
        int h0 = Math.round(h);
        Pixel.inset(g, x0, y0, w0, h0, on ? t.pumpkinDark : t.inset);
        if (on) {
            g.fill(x0 + 2, y0 + 2, x0 + w0 - 2, y0 + h0 - 2, t.pumpkin);
            g.fill(x0 + 2, y0 + 2, x0 + w0 - 2, y0 + 3, t.pumpkinLight);
        }
        int size = h0 - 2;
        int kx = Math.round(x0 + 1 + (w0 - size - 2) * p);
        int light = hov > 0.5f ? 0xFFFFFFFF : t.text;
        Pixel.frame(g, kx, y0 + 1, size, size, hov > 0.5f ? t.text : t.textDim, light, t.textMuted);
    }

    @Override
    public boolean mouseClicked(float mx, float my, int button) {
        if (button == 0 && contains(mx, my)) {
            toggle.set(!state.get());
            return true;
        }
        return false;
    }
}
