package dev.meridian.ui.widget;

import dev.meridian.input.Key;
import dev.meridian.render.Colors;
import dev.meridian.render.Gfx;
import dev.meridian.render.Theme;
import dev.meridian.render.UiDraw;
import dev.meridian.setting.NumberSetting;

/** Horizontal slider with value label. Arrow keys adjust by one step while hovered. */
public final class Slider extends Widget {

    /** Numeric model behind the slider. */
    public interface Model {
        double progress();

        void setProgress(double progress);

        String label();

        /** Moves the value by {@code steps} steps. */
        void nudge(int steps);
    }

    private final Model model;
    private boolean dragging;
    private float trackWidth = 1;

    public Slider(Model model) {
        this.model = model;
        this.h = 12;
    }

    public static Slider of(final NumberSetting setting) {
        return new Slider(new Model() {
            @Override
            public double progress() {
                return setting.progress();
            }

            @Override
            public void setProgress(double progress) {
                setting.setProgress(progress);
            }

            @Override
            public String label() {
                return setting.formatted();
            }

            @Override
            public void nudge(int steps) {
                setting.set(setting.doubleValue() + steps * setting.step());
            }
        });
    }

    @Override
    public void render(Gfx g, float mx, float my) {
        Theme theme = Theme.current();
        float hov = dragging ? 1f : hoverValue(mx, my);
        String value = model.label();
        int valueWidth = g.textWidth(value);
        float trackW = Math.max(1, w - Math.max(valueWidth, 30) - 6);
        trackWidth = trackW;
        float trackY = y + h / 2f - 1.5f;
        float p = (float) model.progress();
        UiDraw.roundRect(g, x, trackY, trackW, 3, 1.5f, theme.borderStrong);
        UiDraw.roundRect(g, x, trackY, Math.max(3, trackW * p), 3, 1.5f, theme.accent);
        float knob = 7 + hov;
        float knobX = x + trackW * p - knob / 2f;
        UiDraw.roundRect(g, knobX, y + h / 2f - knob / 2f, knob, knob, knob / 2f, Colors.lerp(0xFFE6E1F0, 0xFFFFFFFF, hov));
        g.text(value, x + w - valueWidth, y + (h - g.fontHeight()) / 2f + 1, theme.textDim, false);
    }

    @Override
    public boolean mouseClicked(float mx, float my, int button) {
        if (button == 0 && contains(mx, my)) {
            dragging = true;
            update(mx);
            return true;
        }
        return false;
    }

    @Override
    public void mouseDragged(float mx, float my) {
        if (dragging) {
            update(mx);
        }
    }

    @Override
    public void mouseReleased(float mx, float my, int button) {
        dragging = false;
    }

    @Override
    public boolean mouseScrolled(float mx, float my, double amount) {
        if (contains(mx, my)) {
            model.nudge(amount > 0 ? 1 : -1);
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(Key key, boolean ctrl, boolean shift) {
        if (key == Key.LEFT || key == Key.RIGHT) {
            model.nudge((key == Key.RIGHT ? 1 : -1) * (shift ? 10 : 1));
            return true;
        }
        return false;
    }

    private void update(float mx) {
        // the value label occupies the right part; map against the track only
        model.setProgress((mx - x) / trackWidth);
    }
}
