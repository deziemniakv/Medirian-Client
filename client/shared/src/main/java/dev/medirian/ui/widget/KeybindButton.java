package dev.medirian.ui.widget;

import dev.medirian.i18n.I18n;
import dev.medirian.input.Key;
import dev.medirian.render.Colors;
import dev.medirian.render.Gfx;
import dev.medirian.render.Theme;
import dev.medirian.render.Pixel;
import dev.medirian.render.UiDraw;
import dev.medirian.setting.KeySetting;
import dev.medirian.ui.MedirianScreen;

/**
 * Click, then press a key or a mouse side button to bind it. Escape cancels; Backspace/Delete
 * unbinds.
 */
public final class KeybindButton extends Widget {

    private final MedirianScreen screen;
    private final KeySetting setting;
    private boolean listening;

    public KeybindButton(MedirianScreen screen, KeySetting setting) {
        this.screen = screen;
        this.setting = setting;
        this.h = 14;
    }

    @Override
    public void render(Gfx g, float mx, float my) {
        Theme theme = Theme.current();
        float t = hoverValue(mx, my);
        // a key cap: raised, with a thick dark bottom edge
        int fill = listening ? theme.pumpkinDark : Colors.lerp(theme.elevated, theme.surfaceLight, t);
        Pixel.frame(g, x, y, w, h, fill, listening ? theme.pumpkin : theme.surfaceLight, theme.surfaceDark);
        g.fill(Math.round(x) + 1, Math.round(y + h) - 3, Math.round(x + w) - 1, Math.round(y + h) - 1, theme.surfaceDark);
        String label;
        int color;
        if (listening) {
            label = I18n.tr("ui.keybind.listening", "Press a key...");
            color = theme.ember;
        } else if (setting.isBound()) {
            label = setting.key().label();
            color = theme.text;
        } else {
            label = I18n.tr("ui.keybind.none", "None");
            color = theme.textMuted;
        }
        label = UiDraw.ellipsize(g, label, (int) w - 6);
        g.text(label, Math.round(x + (w - g.textWidth(label)) / 2f), Math.round(y + (h - g.fontHeight()) / 2f), color, false);
    }

    @Override
    public boolean mouseClicked(float mx, float my, int button) {
        if (listening) {
            if (button >= 2) {
                finish(Key.fromMouseButton(button));
            } else {
                finish(null);
            }
            return true;
        }
        if (button == 0 && contains(mx, my)) {
            listening = true;
            screen.setFocus(this);
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(Key key, boolean ctrl, boolean shift) {
        if (!listening) {
            return false;
        }
        if (key == Key.ESCAPE) {
            finish(null);
        } else if (key == Key.BACKSPACE || key == Key.DELETE) {
            finish(Key.NONE);
        } else {
            finish(key);
        }
        return true;
    }

    private void finish(Key key) {
        if (key != null) {
            setting.set(key);
        }
        listening = false;
        screen.setFocus(null);
    }

    @Override
    public boolean captureClicks() {
        return listening;
    }

    @Override
    public void focusLost() {
        listening = false;
    }
}
