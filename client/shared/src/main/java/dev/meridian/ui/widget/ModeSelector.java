package dev.meridian.ui.widget;

import dev.meridian.render.Colors;
import dev.meridian.render.Gfx;
import dev.meridian.render.Theme;
import dev.meridian.render.UiDraw;
import dev.meridian.setting.ModeSetting;

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
        UiDraw.roundRectBordered(g, x, y, w, h, 3, Colors.lerp(theme.surface, theme.elevated, t), theme.border);
        String value = UiDraw.ellipsize(g, setting.valueLabel(), (int) w - 22);
        float textY = y + (h - g.fontHeight()) / 2f + 1;
        g.text(value, x + (w - g.textWidth(value)) / 2f, textY, theme.text, false);
        int arrow = Colors.lerp(theme.textMuted, theme.accentHover, t);
        g.text("<", x + 4, textY, arrow, false);
        g.text(">", x + w - 4 - g.textWidth(">"), textY, arrow, false);
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
