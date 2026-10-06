package dev.medirian.ui.widget;

import dev.medirian.input.Key;
import dev.medirian.render.Colors;
import dev.medirian.render.Gfx;
import dev.medirian.render.Theme;
import dev.medirian.render.Pixel;
import dev.medirian.ui.MedirianScreen;

/** Single-line text input with caret, placeholder and Ctrl+Backspace word deletion. */
public final class TextField extends Widget {

    /** Receives the text after every edit. */
    public interface ChangeListener {
        void changed(String text);
    }

    private final MedirianScreen screen;
    private final int maxLength;
    private String text;
    private String placeholder = "";
    private int caret;
    private ChangeListener listener;
    private Runnable onSubmit;
    private boolean focused;
    private long focusTimeMs;

    public TextField(MedirianScreen screen, String initial, int maxLength) {
        this.screen = screen;
        this.maxLength = maxLength;
        this.text = initial == null ? "" : initial;
        this.caret = this.text.length();
        this.h = 14;
    }

    public TextField placeholder(String value) {
        this.placeholder = value;
        return this;
    }

    public TextField onChange(ChangeListener value) {
        this.listener = value;
        return this;
    }

    public TextField onSubmit(Runnable value) {
        this.onSubmit = value;
        return this;
    }

    public String text() {
        return text;
    }

    public void setText(String value) {
        text = value == null ? "" : value;
        caret = text.length();
    }

    public boolean isFocused() {
        return focused;
    }

    /** Focuses the field. Fields created without a screen (nested in another widget) manage focus locally. */
    public void focus() {
        focused = true;
        focusTimeMs = System.currentTimeMillis();
        if (screen != null) {
            screen.setFocus(this);
        }
    }

    @Override
    public void render(Gfx g, float mx, float my) {
        Theme theme = Theme.current();
        float t = hoverValue(mx, my);
        Pixel.inset(g, x, y, w, h, Colors.lerp(theme.inset, theme.surfaceDark, t));
        if (focused) {
            Pixel.outline(g, Math.round(x), Math.round(y), Math.round(w), Math.round(h), theme.accent);
        }
        float textY = Math.round(y + (h - g.fontHeight()) / 2f + 1);
        int available = (int) w - 10;
        g.enableScissor((int) x + 2, (int) y, (int) (x + w) - 2, (int) (y + h));
        if (text.isEmpty() && !focused) {
            g.text(placeholder, x + 5, textY, theme.textMuted, false);
        } else {
            String before = text.substring(0, caret);
            int caretX = g.textWidth(before);
            float scroll = Math.max(0, caretX - available);
            g.text(text, x + 5 - scroll, textY, theme.text, false);
            if (focused && ((System.currentTimeMillis() - focusTimeMs) / 500) % 2 == 0) {
                float cx = x + 5 + caretX - scroll;
                g.fill(Math.round(cx), (int) textY - 1, Math.round(cx) + 1, (int) textY + g.fontHeight() - 2, theme.pumpkinLight);
            }
        }
        g.disableScissor();
    }

    @Override
    public boolean mouseClicked(float mx, float my, int button) {
        if (contains(mx, my)) {
            focus();
            caret = text.length();
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(Key key, boolean ctrl, boolean shift) {
        if (!focused) {
            return false;
        }
        switch (key) {
            case BACKSPACE:
                if (caret > 0) {
                    int start = ctrl ? wordStart() : caret - 1;
                    text = text.substring(0, start) + text.substring(caret);
                    caret = start;
                    changed();
                }
                return true;
            case DELETE:
                if (caret < text.length()) {
                    text = text.substring(0, caret) + text.substring(caret + 1);
                    changed();
                }
                return true;
            case LEFT:
                caret = Math.max(0, caret - 1);
                return true;
            case RIGHT:
                caret = Math.min(text.length(), caret + 1);
                return true;
            case HOME:
                caret = 0;
                return true;
            case END:
                caret = text.length();
                return true;
            case ENTER:
                if (onSubmit != null) {
                    onSubmit.run();
                }
                return true;
            case ESCAPE:
                if (screen != null) {
                    screen.setFocus(null);
                } else {
                    focused = false;
                }
                return true;
            default:
                return key != Key.NONE && !key.isMouse() && key.label().length() == 1; // swallow typing keys
        }
    }

    private int wordStart() {
        int i = caret - 1;
        while (i > 0 && text.charAt(i - 1) == ' ') {
            i--;
        }
        while (i > 0 && text.charAt(i - 1) != ' ') {
            i--;
        }
        return Math.max(0, i);
    }

    @Override
    public boolean charTyped(char c) {
        if (!focused || c < 32 || c == 127 || text.length() >= maxLength) {
            return focused;
        }
        text = text.substring(0, caret) + c + text.substring(caret);
        caret++;
        changed();
        return true;
    }

    private void changed() {
        if (listener != null) {
            listener.changed(text);
        }
    }

    @Override
    public void focusLost() {
        focused = false;
    }
}
