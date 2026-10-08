package dev.medirian.ui;

import dev.medirian.i18n.I18n;
import dev.medirian.input.Key;
import dev.medirian.render.Gfx;
import dev.medirian.render.Pixel;
import dev.medirian.render.Theme;
import dev.medirian.render.UiDraw;
import dev.medirian.setting.ActionSetting;
import dev.medirian.setting.BooleanSetting;
import dev.medirian.setting.ColorSetting;
import dev.medirian.setting.KeySetting;
import dev.medirian.setting.ModeSetting;
import dev.medirian.setting.NumberSetting;
import dev.medirian.setting.Setting;
import dev.medirian.setting.TextSetting;
import dev.medirian.ui.widget.Button;
import dev.medirian.ui.widget.ColorButton;
import dev.medirian.ui.widget.KeybindButton;
import dev.medirian.ui.widget.ModeSelector;
import dev.medirian.ui.widget.ScrollState;
import dev.medirian.ui.widget.Slider;
import dev.medirian.ui.widget.Switch;
import dev.medirian.ui.widget.TextField;
import dev.medirian.ui.widget.Widget;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Scrollable list of labelled rows ("label …… control"). Builds the right control for every
 * {@link Setting} type, inserts group headers and supports custom rows (info, actions).
 */
public final class SettingsList {

    private static final float ROW_HEIGHT = 20;
    private static final float DESC_HEIGHT = 10;

    private final MedirianScreen screen;
    private final List<Row> rows = new ArrayList<Row>();
    private final ScrollState scroll = new ScrollState();
    public float x;
    public float y;
    public float w;
    public float h;

    public SettingsList(MedirianScreen screen) {
        this.screen = screen;
    }

    public SettingsList bounds(float x, float y, float w, float h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        return this;
    }

    public void clear() {
        rows.clear();
        scroll.reset();
    }

    public SettingsList add(Row row) {
        rows.add(row);
        return this;
    }

    /** Adds rows for settings, inserting a header whenever the setting group changes. */
    public SettingsList addSettings(List<Setting<?>> settings) {
        String group = null;
        for (Setting<?> setting : settings) {
            String next = setting.group();
            if (next != null && !next.equals(group)) {
                add(new HeaderRow(I18n.tr("group." + next.toLowerCase().replace(' ', '_'), next)));
            }
            group = next;
            add(new SettingRow(screen, setting));
        }
        return this;
    }

    public boolean isEmpty() {
        return rows.isEmpty();
    }

    // ------------------------------------------------------------------ rendering & input

    public void render(Gfx g, float mx, float my) {
        float content = 0;
        for (Row row : rows) {
            if (row.visible()) {
                content += row.height();
            }
        }
        scroll.setBounds(content, h);
        float offset = scroll.offset();
        boolean inside = mx >= x && my >= y && mx < x + w && my < y + h;
        float rowY = y - offset;
        g.enableScissor((int) x, (int) y, (int) (x + w), (int) (y + h));
        for (Row row : rows) {
            if (!row.visible()) {
                continue;
            }
            float rh = row.height();
            row.layout(x, rowY, w - 6);
            if (rowY + rh > y && rowY < y + h) {
                row.render(g, inside ? mx : -1, inside ? my : -1);
            }
            rowY += rh;
        }
        g.disableScissor();
        scroll.renderBar(g, x + w - 2, y);
    }

    public boolean mouseClicked(float mx, float my, int button) {
        if (mx < x || my < y || mx >= x + w || my >= y + h) {
            return false;
        }
        for (Row row : rows) {
            Widget control = row.control();
            if (row.visible() && control != null && control.mouseClicked(mx, my, button)) {
                return true;
            }
        }
        return false;
    }

    public void mouseReleased(float mx, float my, int button) {
        for (Row row : rows) {
            if (row.control() != null) {
                row.control().mouseReleased(mx, my, button);
            }
        }
    }

    public void mouseDragged(float mx, float my) {
        for (Row row : rows) {
            if (row.control() != null) {
                row.control().mouseDragged(mx, my);
            }
        }
    }

    public boolean mouseScrolled(float mx, float my, double amount) {
        if (mx < x || my < y || mx >= x + w || my >= y + h) {
            return false;
        }
        for (Row row : rows) {
            Widget control = row.control();
            if (row.visible() && control instanceof Slider && control.mouseScrolled(mx, my, amount)) {
                return true;
            }
        }
        scroll.scroll(amount);
        return true;
    }

    /** Arrow keys adjust the hovered slider. */
    public boolean keyPressed(Key key, boolean ctrl, boolean shift, float mx, float my) {
        for (Row row : rows) {
            Widget control = row.control();
            if (row.visible() && control instanceof Slider && control.contains(mx, my)) {
                return control.keyPressed(key, ctrl, shift);
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ rows

    /** Widens a button to the left so its whole label shows (translations differ in length). */
    static void fitButton(Gfx g, Widget control) {
        if (control instanceof Button) {
            float needed = ((Button) control).preferredWidth(g, control.w);
            if (needed > control.w) {
                control.bounds(control.x + control.w - needed, control.y, needed, control.h);
            }
        }
    }

    /** One line of the list. */
    public abstract static class Row {
        protected float rx;
        protected float ry;
        protected float rw;
        /** Lines the description took when last drawn (1 or 2); rows grow to fit it. */
        protected int descriptionLines = 1;

        /** Height of a row with a description of {@link #descriptionLines} lines. */
        protected float describedHeight() {
            return ROW_HEIGHT + DESC_HEIGHT * descriptionLines - 2;
        }

        public float height() {
            return ROW_HEIGHT;
        }

        public boolean visible() {
            return true;
        }

        public void layout(float x, float y, float w) {
            rx = x;
            ry = y;
            rw = w;
        }

        public abstract void render(Gfx g, float mx, float my);

        public Widget control() {
            return null;
        }

        protected void label(Gfx g, String text, String description, float maxWidth) {
            Theme theme = Theme.current();
            if (description == null) {
                g.text(UiDraw.ellipsize(g, text, (int) maxWidth), rx, ry + (ROW_HEIGHT - g.fontHeight()) / 2f + 1, theme.text, false);
                return;
            }
            g.text(UiDraw.ellipsize(g, text, (int) maxWidth), rx, ry + 4, theme.text, false);
            // descriptions wrap onto a second line instead of being cut off
            java.util.List<String> lines = UiDraw.wrap(g, description, (int) maxWidth);
            descriptionLines = Math.max(1, Math.min(2, lines.size()));
            for (int i = 0; i < descriptionLines; i++) {
                String line = lines.get(i);
                if (i == 1 && lines.size() > 2) {
                    line = UiDraw.ellipsize(g, line + " " + lines.get(2), (int) maxWidth);
                }
                g.text(line, rx, ry + 15 + i * DESC_HEIGHT, theme.textMuted, false);
            }
        }
    }

    /** Section header. */
    public static final class HeaderRow extends Row {
        private final String text;

        public HeaderRow(String text) {
            this.text = text;
        }

        @Override
        public float height() {
            return 20;
        }

        @Override
        public void render(Gfx g, float mx, float my) {
            Theme theme = Theme.current();
            String upper = text.toUpperCase();
            Pixel.diamond(g, rx, ry + 9, theme.pumpkin);
            g.text(upper, rx + 6, ry + 8, theme.pumpkinLight, false);
            float lineX = rx + 6 + g.textWidth(upper) + 6;
            if (lineX < rx + rw) {
                Pixel.groove(g, lineX, ry + 11, rx + rw - lineX);
            }
        }
    }

    /** A row for any {@link Setting}. */
    public static final class SettingRow extends Row {
        private final Setting<?> setting;
        private final Widget control;
        private final float controlWidth;

        public SettingRow(MedirianScreen screen, Setting<?> setting) {
            this.setting = setting;
            if (setting instanceof BooleanSetting) {
                final BooleanSetting b = (BooleanSetting) setting;
                control = new Switch(b::on, b::set);
                controlWidth = 22;
            } else if (setting instanceof NumberSetting) {
                control = Slider.of((NumberSetting) setting);
                controlWidth = -1; // computed in layout
            } else if (setting instanceof ModeSetting) {
                control = new ModeSelector((ModeSetting<?>) setting);
                controlWidth = 104;
            } else if (setting instanceof ColorSetting) {
                control = new ColorButton(screen, (ColorSetting) setting);
                controlWidth = 36;
            } else if (setting instanceof KeySetting) {
                control = new KeybindButton(screen, (KeySetting) setting);
                controlWidth = 72;
            } else if (setting instanceof TextSetting) {
                final TextSetting text = (TextSetting) setting;
                control = new TextField(screen, text.get(), text.maxLength()).onChange(text::set);
                controlWidth = -1;
            } else if (setting instanceof ActionSetting) {
                final ActionSetting action = (ActionSetting) setting;
                control = new Button(action::buttonLabel, Button.Style.SECONDARY, action::run);
                controlWidth = 72;
            } else {
                control = null;
                controlWidth = 0;
            }
        }

        @Override
        public float height() {
            return setting.displayDescription() != null ? describedHeight() : ROW_HEIGHT;
        }

        @Override
        public boolean visible() {
            return setting.isVisible();
        }

        @Override
        public void layout(float x, float y, float w) {
            super.layout(x, y, w);
            if (control == null) {
                return;
            }
            float cw = controlWidth < 0 ? Math.min(150, w * 0.5f) : controlWidth;
            control.bounds(x + w - cw, y + (height() - control.h) / 2f, cw, control.h);
        }

        @Override
        public void render(Gfx g, float mx, float my) {
            fitButton(g, control);
            float labelWidth = control == null ? rw : rw - control.w - 10;
            label(g, setting.displayName(), setting.displayDescription(), labelWidth);
            if (control != null) {
                control.render(g, mx, my);
            }
        }

        @Override
        public Widget control() {
            return control;
        }
    }

    /** Read-only "label: value" row. */
    public static final class InfoRow extends Row {
        private final String label;
        private final Supplier<String> value;

        public InfoRow(String label, Supplier<String> value) {
            this.label = label;
            this.value = value;
        }

        @Override
        public void render(Gfx g, float mx, float my) {
            Theme theme = Theme.current();
            String text = value.get();
            int valueWidth = g.textWidth(text);
            label(g, label, null, rw - valueWidth - 10);
            g.text(text, rx + rw - valueWidth, ry + (ROW_HEIGHT - g.fontHeight()) / 2f + 1, theme.textDim, false);
        }
    }

    /** A row shown only while {@code when} holds (e.g. a module's settings while it is on). */
    public static final class GuardedRow extends Row {
        private final Row inner;
        private final java.util.function.BooleanSupplier when;

        public GuardedRow(Row inner, java.util.function.BooleanSupplier when) {
            this.inner = inner;
            this.when = when;
        }

        @Override
        public float height() {
            return inner.height();
        }

        @Override
        public boolean visible() {
            return when.getAsBoolean() && inner.visible();
        }

        @Override
        public void layout(float x, float y, float w) {
            super.layout(x, y, w);
            inner.layout(x, y, w);
        }

        @Override
        public void render(Gfx g, float mx, float my) {
            inner.render(g, mx, my);
        }

        @Override
        public Widget control() {
            return inner.control();
        }
    }

    /** Label with one control on the right (button, switch…). */
    public static final class ControlRow extends Row {
        private final String label;
        private final String description;
        private final Widget control;
        private final float controlWidth;

        public ControlRow(String label, String description, Widget control, float controlWidth) {
            this.label = label;
            this.description = description;
            this.control = control;
            this.controlWidth = controlWidth;
        }

        @Override
        public float height() {
            return description != null ? describedHeight() : ROW_HEIGHT;
        }

        @Override
        public void layout(float x, float y, float w) {
            super.layout(x, y, w);
            float ch = control.h > 0 ? control.h : 14;
            control.bounds(x + w - controlWidth, y + (height() - ch) / 2f, controlWidth, ch);
        }

        @Override
        public void render(Gfx g, float mx, float my) {
            fitButton(g, control);
            label(g, label, description, rw - control.w - 10);
            control.render(g, mx, my);
        }

        @Override
        public Widget control() {
            return control;
        }
    }
}
