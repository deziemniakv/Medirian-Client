package dev.meridian.hud;

import dev.meridian.module.Module;
import dev.meridian.render.Gfx;
import dev.meridian.setting.BooleanSetting;

/**
 * HUD element made of one or more "label: value" lines.
 *
 * <p>Text is rebuilt at most every {@link #refreshIntervalMs()} milliseconds and cached together
 * with its measured width, so rendering a frame is just a few draw calls. Lines are aligned
 * according to the element's anchor (left/centre/right).
 */
public abstract class TextHudElement extends HudElement {

    private static final int PAD_X = 5;
    private static final int PAD_Y = 4;
    private static final int LINE_GAP = 2;
    private static final int MAX_LINES = 8;

    /** Reusable line buffer passed to {@link #collect}. */
    public static final class Lines {
        final String[] labels = new String[MAX_LINES];
        final String[] values = new String[MAX_LINES];
        int count;

        public void add(String label, String value) {
            if (count < MAX_LINES) {
                labels[count] = label;
                values[count] = value;
                count++;
            }
        }

        public void add(String value) {
            add(null, value);
        }
    }

    protected final HudStyle style;
    protected final BooleanSetting showLabel;
    protected final BooleanSetting brackets;

    private final Lines lines = new Lines();
    private final String[] prefixes = new String[MAX_LINES];
    private final String[] suffixes = new String[MAX_LINES];
    private final int[] prefixWidths = new int[MAX_LINES];
    private final int[] lineWidths = new int[MAX_LINES];
    private long nextRefreshMs;
    private boolean lastEditor;
    private int textHeight;

    protected TextHudElement(Module module, Anchor anchor, float x, float y) {
        super(module, anchor, x, y);
        this.style = new HudStyle(module, true);
        this.showLabel = module.add(new BooleanSetting("showLabel", "Show label", true).group(HudStyle.GROUP));
        this.brackets = module.add(new BooleanSetting("brackets", "Brackets", false).group(HudStyle.GROUP));
    }

    /** Fills {@code out} with the lines to display. Called at most every {@link #refreshIntervalMs()}. */
    protected abstract void collect(Lines out, boolean editor);

    protected long refreshIntervalMs() {
        return 100;
    }

    /** Height of an optional area drawn below the text box by {@link #renderExtra} (0 = none). */
    protected int extraHeight() {
        return 0;
    }

    /** Draws the optional area below the text box, starting at {@code y}. */
    protected void renderExtra(Gfx g, int y, boolean editor) {
    }

    /** Height of the text box (without the extra area). */
    protected final int textHeight() {
        return textHeight;
    }

    /** Forces the text to be rebuilt on the next frame. */
    protected final void invalidate() {
        nextRefreshMs = 0;
    }

    @Override
    public void render(Gfx g, boolean editor) {
        long now = System.currentTimeMillis();
        if (now >= nextRefreshMs || editor != lastEditor) {
            rebuild(g, editor);
            nextRefreshMs = now + refreshIntervalMs();
            lastEditor = editor;
        }
        if (lines.count == 0) {
            return;
        }
        style.drawBackground(g, width, textHeight);
        int fontHeight = g.fontHeight();
        boolean shadow = style.shadow.on();
        int labelColor = style.labelColor.argb();
        int textColor = style.textColor.argb();
        float align = anchor().fx;
        for (int i = 0; i < lines.count; i++) {
            float x = PAD_X + (width - PAD_X * 2 - lineWidths[i]) * align;
            float y = PAD_Y + i * (fontHeight + LINE_GAP);
            if (prefixes[i] != null) {
                g.text(prefixes[i], x, y, labelColor, shadow);
            }
            g.text(suffixes[i], x + prefixWidths[i], y, textColor, shadow);
            if (brackets.on()) {
                g.text("]", x + lineWidths[i] - g.textWidth("]"), y, labelColor, shadow);
            }
        }
        if (extraHeight() > 0) {
            renderExtra(g, textHeight + 2, editor);
        }
    }

    private void rebuild(Gfx g, boolean editor) {
        lines.count = 0;
        collect(lines, editor);
        boolean label = showLabel.on();
        boolean bracket = brackets.on();
        int maxWidth = 0;
        for (int i = 0; i < lines.count; i++) {
            String prefix = label && lines.labels[i] != null ? lines.labels[i] + ": " : "";
            if (bracket) {
                prefix = "[" + prefix;
            }
            prefixes[i] = prefix.isEmpty() ? null : prefix;
            suffixes[i] = lines.values[i] == null ? "" : lines.values[i];
            prefixWidths[i] = prefix.isEmpty() ? 0 : g.textWidth(prefix);
            lineWidths[i] = prefixWidths[i] + g.textWidth(suffixes[i]) + (bracket ? g.textWidth("]") : 0);
            maxWidth = Math.max(maxWidth, lineWidths[i]);
        }
        int fontHeight = g.fontHeight();
        width = maxWidth + PAD_X * 2;
        textHeight = lines.count == 0 ? 0 : lines.count * fontHeight + (lines.count - 1) * LINE_GAP + PAD_Y * 2 - 1;
        int extra = extraHeight();
        height = extra > 0 ? textHeight + 2 + extra : textHeight;
    }
}
