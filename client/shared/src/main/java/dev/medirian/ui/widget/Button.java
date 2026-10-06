package dev.medirian.ui.widget;

import dev.medirian.render.Colors;
import dev.medirian.render.Gfx;
import dev.medirian.render.Pixel;
import dev.medirian.render.Theme;
import dev.medirian.render.UiDraw;

/** Pixel button: a raised block that sinks a pixel while pressed, optionally with an icon. */
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
    private String icon;

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
    public float preferredWidth(Gfx g, float min) {
        return Math.max(min, g.textWidth(label.get()) + 14 + (icon != null ? 18 : 0));
    }

    public Button style(Style newStyle) {
        this.style = newStyle;
        return this;
    }

    public Button enabled(boolean on) {
        this.enabled = on;
        return this;
    }

    /** Adds a 16×16 icon from the atlas before the label. */
    public Button icon(String name) {
        this.icon = name;
        return this;
    }

    @Override
    public void render(Gfx g, float mx, float my) {
        Theme t = Theme.current();
        float hov = enabled ? hoverValue(mx, my) : 0f;
        boolean hot = hov > 0.5f;
        int fill;
        int light;
        int dark;
        int text;
        switch (style) {
            case PRIMARY:
                fill = hot ? t.pumpkinLight : t.pumpkin;
                light = hot ? t.ember : t.pumpkinLight;
                dark = t.pumpkinDark;
                text = t.onPumpkin;
                break;
            case DANGER:
                fill = hot ? 0xFFE0645F : t.danger;
                light = 0xFFEC8A84;
                dark = 0xFF8C2A2F;
                text = 0xFFFFFFFF;
                break;
            case GHOST:
                fill = 0;
                light = 0;
                dark = 0;
                text = Colors.lerp(t.textDim, t.text, hov);
                break;
            default:
                fill = hot ? t.surfaceLight : t.elevated;
                light = hot ? 0xFF4C3870 : t.surfaceLight;
                dark = t.surfaceDark;
                text = t.text;
                break;
        }
        if (!enabled) {
            fill = Colors.lerp(fill, t.panel, 0.6f);
            light = Colors.lerp(light, t.panel, 0.6f);
            dark = Colors.lerp(dark, t.panel, 0.6f);
            text = t.textMuted;
        }
        if (style == Style.GHOST) {
            if (hov > 0) {
                g.fill(Math.round(x), Math.round(y), Math.round(x + w), Math.round(y + h), Colors.fade(t.surfaceLight, hov * 0.6f));
            }
        } else {
            Pixel.frame(g, x, y, w, h, fill, light, dark);
        }
        float inner = icon != null ? (label.get().isEmpty() ? 16 : 18) : 0;
        String value = label.get().isEmpty() ? "" : UiDraw.ellipsize(g, label.get(), (int) (w - 10 - inner));
        float contentW = g.textWidth(value) + inner;
        float cx = Math.round(x + (w - contentW) / 2f);
        if (icon != null) {
            Pixel.icon(g, icon, cx, Math.round(y + (h - 16) / 2f), 1, enabled ? 0xFFFFFFFF : 0x80FFFFFF);
        }
        g.text(value, cx + inner, Math.round(y + (h - g.fontHeight()) / 2f + 1), text, false);
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
