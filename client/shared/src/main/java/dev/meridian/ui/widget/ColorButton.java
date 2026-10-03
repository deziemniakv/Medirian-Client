package dev.meridian.ui.widget;

import dev.meridian.input.Key;
import dev.meridian.render.Colors;
import dev.meridian.render.Gfx;
import dev.meridian.render.Theme;
import dev.meridian.render.UiDraw;
import dev.meridian.setting.ColorSetting;
import dev.meridian.ui.MeridianScreen;

/**
 * Colour swatch; clicking opens a picker popover with saturation/value field, hue bar,
 * opacity bar and a hex input.
 */
public final class ColorButton extends Widget {

    private static final int PICKER_W = 132;
    private static final int FIELD_H = 64;

    private final MeridianScreen screen;
    private final ColorSetting setting;
    private boolean open;
    private float px;
    private float py;
    private float hue;
    private float sat;
    private float val;
    private int dragging; // 0 none, 1 field, 2 hue, 3 alpha
    private final TextField hex;

    public ColorButton(MeridianScreen screen, ColorSetting setting) {
        this.screen = screen;
        this.setting = setting;
        this.w = 36;
        this.h = 14;
        this.hex = new TextField(null, ColorSetting.toHex(setting.argb()), 9);
        this.hex.onChange(text -> {
            Integer parsed = ColorSetting.parseHex(text);
            if (parsed != null) {
                setting.set(parsed);
                syncHsv();
            }
        });
    }

    @Override
    public void render(Gfx g, float mx, float my) {
        Theme theme = Theme.current();
        float t = hoverValue(mx, my);
        UiDraw.roundRectBordered(g, x, y, w, h, 3, theme.surface, open ? theme.accent : Colors.lerp(theme.border, theme.borderStrong, t));
        checker(g, (int) x + 3, (int) y + 3, (int) w - 6, (int) h - 6);
        g.fill((int) x + 3, (int) y + 3, (int) (x + w) - 3, (int) (y + h) - 3, setting.argb());
    }

    private static void checker(Gfx g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0xFFBDBDBD);
        for (int cy = 0; cy < h; cy += 3) {
            for (int cx = ((cy / 3) % 2) * 3; cx < w; cx += 6) {
                g.fill(x + cx, y + cy, Math.min(x + w, x + cx + 3), Math.min(y + h, y + cy + 3), 0xFF7E7E7E);
            }
        }
    }

    private float pickerHeight() {
        return FIELD_H + (setting.allowsAlpha() ? 34 : 24) + 22;
    }

    @Override
    public void renderOverlay(Gfx g, float mx, float my) {
        if (!open) {
            return;
        }
        Theme theme = Theme.current();
        float ph = pickerHeight();
        UiDraw.shadow(g, px, py, PICKER_W, ph, 5, 3);
        UiDraw.roundRectBordered(g, px, py, PICKER_W, ph, 5, theme.panel, theme.borderStrong);
        int fx = (int) px + 6;
        int fy = (int) py + 6;
        int fw = PICKER_W - 12;
        // saturation (x) / value (y) field: one vertical gradient per column
        for (int i = 0; i < fw; i++) {
            float s = i / (float) (fw - 1);
            g.gradient(fx + i, fy, fx + i + 1, fy + FIELD_H, Colors.hsv(hue, s, 1f, 255), 0xFF000000);
        }
        float knobX = fx + sat * (fw - 1);
        float knobY = fy + (1f - val) * FIELD_H;
        UiDraw.hairline(g, knobX - 2, knobY - 2, 5, 5, 0xFFFFFFFF);
        // hue bar
        int hy = fy + FIELD_H + 5;
        for (int i = 0; i < fw; i++) {
            g.fill(fx + i, hy, fx + i + 1, hy + 7, Colors.hsv(i / (float) fw, 1f, 1f, 255));
        }
        g.fill((int) (fx + hue * fw), hy - 1, (int) (fx + hue * fw) + 1, hy + 8, 0xFFFFFFFF);
        int next = hy + 12;
        if (setting.allowsAlpha()) {
            checker(g, fx, next, fw, 7);
            int opaque = setting.argb() | 0xFF000000;
            for (int i = 0; i < fw; i++) {
                g.fill(fx + i, next, fx + i + 1, next + 7, Colors.withAlpha(opaque, Math.round(255f * i / (fw - 1))));
            }
            float alphaX = fx + (Colors.alpha(setting.argb()) / 255f) * (fw - 1);
            g.fill((int) alphaX, next - 1, (int) alphaX + 1, next + 8, 0xFFFFFFFF);
            next += 12;
        }
        hex.bounds(fx, next, fw, 14);
        hex.render(g, mx, my);
    }

    @Override
    public boolean mouseClicked(float mx, float my, int button) {
        if (!open) {
            if (button == 0 && contains(mx, my)) {
                open = true;
                syncHsv();
                hex.setText(ColorSetting.toHex(setting.argb()));
                px = Math.max(4, Math.min(x, screen.width() - PICKER_W - 4));
                py = y + h + 3;
                if (py + pickerHeight() > screen.height() - 4) {
                    py = y - pickerHeight() - 3;
                }
                screen.setFocus(this);
                return true;
            }
            return false;
        }
        if (mx < px || my < py || mx >= px + PICKER_W || my >= py + pickerHeight()) {
            close();
            return contains(mx, my);
        }
        if (hex.contains(mx, my)) {
            hex.mouseClicked(mx, my, button);
            return true;
        }
        dragging = region(mx, my);
        update(mx, my);
        return true;
    }

    private int region(float mx, float my) {
        float fy = py + 6;
        if (my < fy + FIELD_H) {
            return 1;
        }
        if (my < fy + FIELD_H + 13) {
            return 2;
        }
        if (setting.allowsAlpha() && my < fy + FIELD_H + 25) {
            return 3;
        }
        return 0;
    }

    @Override
    public void mouseDragged(float mx, float my) {
        update(mx, my);
    }

    @Override
    public void mouseReleased(float mx, float my, int button) {
        dragging = 0;
    }

    private void update(float mx, float my) {
        float fx = px + 6;
        float fw = PICKER_W - 12;
        float rx = clamp((mx - fx) / fw);
        int alpha = Colors.alpha(setting.argb());
        switch (dragging) {
            case 1:
                sat = rx;
                val = 1f - clamp((my - (py + 6)) / FIELD_H);
                break;
            case 2:
                hue = Math.min(0.999f, rx);
                break;
            case 3:
                alpha = Math.round(rx * 255);
                break;
            default:
                return;
        }
        setting.set(Colors.hsv(hue, sat, val, setting.allowsAlpha() ? alpha : 255));
        hex.setText(ColorSetting.toHex(setting.argb()));
    }

    private static float clamp(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private void syncHsv() {
        float[] hsv = Colors.toHsv(setting.argb());
        // keep the hue when the colour is grey (hue undefined)
        if (hsv[1] > 0.001f) {
            hue = hsv[0];
        }
        sat = hsv[1];
        val = hsv[2];
    }

    @Override
    public boolean keyPressed(Key key, boolean ctrl, boolean shift) {
        if (!open) {
            return false;
        }
        if (hex.isFocused() && key != Key.ESCAPE) {
            return hex.keyPressed(key, ctrl, shift);
        }
        if (key == Key.ESCAPE || key == Key.ENTER) {
            close();
            return true;
        }
        return false;
    }

    @Override
    public boolean charTyped(char c) {
        return open && hex.isFocused() && hex.charTyped(c);
    }

    private void close() {
        open = false;
        dragging = 0;
        hex.focusLost();
        screen.setFocus(null);
    }

    @Override
    public boolean captureClicks() {
        return open;
    }

    @Override
    public void focusLost() {
        open = false;
        hex.focusLost();
    }
}
