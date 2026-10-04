package dev.meridian.ui.widget;

import dev.meridian.render.Colors;
import dev.meridian.render.Gfx;
import dev.meridian.render.Theme;
import dev.meridian.render.UiDraw;

/** Text button. */
public class Button extends Widget {

    /** Visual weight. */
    public enum Style { PRIMARY, SECONDARY, GHOST, DANGER }

    /** Supplies the label each frame (allows dynamic labels like "Confirm?"). */
    public interface Label {
        String get();
    }

    private final Label label;
    private final Runnable action;
    private Style style;
    private boolean enabled = true;

    public Button(Label label, Style style, Runnable action) {
        this.label = label;
        this.style = style;
        this.action = action;
        this.h = 14;
    }

    public Button(final String label, Style style, Runnable action) {
        this(() -> label, style, action);
    }

    /** The current label. */
    public String text() {
        return label.get();
    }

    /** Width that shows the whole label, at least {@code min}. */
    public float preferredWidth(dev.meridian.render.Gfx g, float min) {
        return Math.max(min, g.textWidth(label.get()) + 14);
    }

    public Button style(Style newStyle) {
        this.style = newStyle;
        return this;
    }

    public Button enabled(boolean on) {
        this.enabled = on;
        return this;
    }

    @Override
    public void render(Gfx g, float mx, float my) {
        Theme theme = Theme.current();
        float t = enabled ? hoverValue(mx, my) : 0f;
        int fill;
        int text;
        int border = 0;
        switch (style) {
            case PRIMARY:
                fill = Colors.lerp(theme.accent, theme.accentHover, t);
                text = 0xFFFFFFFF;
                break;
            case DANGER:
                fill = Colors.lerp(Colors.withAlpha(theme.danger, 0x30), Colors.withAlpha(theme.danger, 0x55), t);
                text = theme.text;
                break;
            case GHOST:
                fill = Colors.fade(theme.elevated, t);
                text = Colors.lerp(theme.textDim, theme.text, t);
                break;
            default:
                fill = Colors.lerp(theme.elevated, theme.borderStrong, t * 0.6f);
                text = theme.text;
                border = theme.border;
                break;
        }
        if (!enabled) {
            fill = Colors.fade(fill, 0.5f);
            text = theme.textMuted;
        }
        if (border != 0) {
            UiDraw.roundRectBordered(g, x, y, w, h, 3, fill, border);
        } else {
            UiDraw.roundRect(g, x, y, w, h, 3, fill);
        }
        String value = UiDraw.ellipsize(g, label.get(), (int) w - 8);
        g.text(value, x + (w - g.textWidth(value)) / 2f, y + (h - g.fontHeight()) / 2f + 1, text, false);
    }

    @Override
    public boolean mouseClicked(float mx, float my, int button) {
        if (enabled && button == 0 && contains(mx, my)) {
            action.run();
            return true;
        }
        return false;
    }
}
