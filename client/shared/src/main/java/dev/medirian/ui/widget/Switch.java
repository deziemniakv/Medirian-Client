package dev.medirian.ui.widget;

import dev.medirian.render.Anim;
import dev.medirian.render.Colors;
import dev.medirian.render.Gfx;
import dev.medirian.render.Theme;
import dev.medirian.render.UiDraw;

/** On/off toggle switch (22×12). */
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
        this.knob = new Anim(state.get() ? 1f : 0f, 16f);
        this.w = 22;
        this.h = 12;
    }

    @Override
    public void render(Gfx g, float mx, float my) {
        Theme theme = Theme.current();
        float t = knob.target(state.get() ? 1f : 0f).get();
        float hov = hoverValue(mx, my);
        int track = Colors.lerp(Colors.lerp(theme.borderStrong, theme.textMuted, hov * 0.4f), theme.accent, t);
        UiDraw.roundRect(g, x, y, w, h, h / 2f, track);
        float knobSize = h - 4;
        float knobX = x + 2 + (w - knobSize - 4) * t;
        UiDraw.roundRect(g, knobX, y + 2, knobSize, knobSize, knobSize / 2f, Colors.lerp(0xFFB9B3C9, 0xFFFFFFFF, t));
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
