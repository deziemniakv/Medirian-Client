package dev.medirian.ui.widget;

import dev.medirian.render.Colors;
import dev.medirian.render.Gfx;
import dev.medirian.render.Theme;
import dev.medirian.render.Pixel;
import dev.medirian.render.UiDraw;
import dev.medirian.setting.ModeSetting;

/** "‹ Value ›" selector for {@link ModeSetting}: left half goes back, right half forward. */
public final class ModeSelector extends Widget {

    private final ModeSetting<?> setting;

    public ModeSelector(ModeSetting<?> setting) {
        this.setting = setting;
        this.h = 14;
    }

    @Override
    public void render(Gfx g, float mx, float my) {
        Theme theme = Theme.current();
        float t = hoverValue(mx, my);
        Pixel.inset(g, x, y, w, h, Colors.lerp(theme.inset, theme.surfaceDark, t));
        String value = UiDraw.ellipsize(g, setting.valueLabel(), (int) w - 24);
        float textY = Math.round(y + (h - g.fontHeight()) / 2f + 1);
        g.text(value, Math.round(x + (w - g.textWidth(value)) / 2f), textY, theme.text, false);
        boolean left = t > 0.5f && mx < x + w / 3f;
        boolean right = t > 0.5f && mx >= x + w / 3f;
        arrow(g, Math.round(x + 5), Math.round(y + h / 2f), -1, left ? theme.pumpkinLight : theme.textMuted);
        arrow(g, Math.round(x + w - 6), Math.round(y + h / 2f), 1, right ? theme.pumpkinLight : theme.textMuted);
    }

    /** A 3×5 pixel triangle pointing left (-1) or right (1). */
    private static void arrow(Gfx g, int x, int cy, int dir, int argb) {
        for (int i = 0; i < 3; i++) {
            int col = dir > 0 ? x + i - 1 : x - i + 1;
            g.fill(col, cy - 2 + i, col + 1, cy + 3 - i, argb);
        }
    }

    @Override
    public boolean mouseClicked(float mx, float my, int button) {
        if (!contains(mx, my)) {
            return false;
        }
        boolean back = button == 1 || mx < x + w / 3f;
        setting.cycle(back ? -1 : 1);
        return true;
    }
}
