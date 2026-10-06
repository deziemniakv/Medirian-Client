package dev.medirian.ui.widget;

import dev.medirian.input.Key;
import dev.medirian.render.Gfx;
import dev.medirian.render.Theme;
import dev.medirian.render.Pixel;
import dev.medirian.setting.NumberSetting;

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
        int trackW = Math.max(8, Math.round(w - Math.max(valueWidth, 30) - 6));
        trackWidth = trackW;
        int x0 = Math.round(x);
        int trackY = Math.round(y + h / 2f) - 3;
        float p = (float) model.progress();
        Pixel.inset(g, x0, trackY, trackW, 6, theme.inset);
        int filled = Math.round((trackW - 4) * p);
        if (filled > 0) {
            g.fill(x0 + 2, trackY + 2, x0 + 2 + filled, trackY + 4, theme.pumpkin);
        }
        // a Minecraft-style block knob
        int knobX = Math.round(x0 + 1 + (trackW - 8) * p);
        Pixel.frame(g, knobX, Math.round(y), 6, Math.round(h), hov > 0.5f ? theme.text : theme.textDim,
                hov > 0.5f ? 0xFFFFFFFF : theme.text, theme.textMuted);
        g.text(value, x + w - valueWidth, Math.round(y + (h - g.fontHeight()) / 2f + 1), hov > 0.5f ? theme.text : theme.textDim, false);
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
