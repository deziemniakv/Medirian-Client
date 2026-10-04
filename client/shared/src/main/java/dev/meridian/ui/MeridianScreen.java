package dev.meridian.ui;

import dev.meridian.core.Meridian;
import dev.meridian.input.Key;
import dev.meridian.render.Anim;
import dev.meridian.render.Colors;
import dev.meridian.render.Gfx;
import dev.meridian.render.Theme;
import dev.meridian.ui.widget.Widget;

/**
 * Base class of Meridian's in-game screens. Version adapters wrap it in a native screen and
 * forward input in Minecraft GUI units through the {@code on*} methods; this class converts them
 * into Meridian's virtual units ({@link UiScale}) so subclasses never deal with GUI scale.
 */
public abstract class MeridianScreen {

    protected final MeridianScreen parent;
    protected int width;
    protected int height;
    private float factor = 1f;
    private int guiWidth = -1;
    private int guiHeight = -1;
    private double guiScale = -1;
    private Widget focused;
    private float lastMouseX;
    private float lastMouseY;

    protected MeridianScreen(MeridianScreen parent) {
        this.parent = parent;
    }

    // ------------------------------------------------------------------ adapter-facing API (GUI units)

    public final void renderFrame(Gfx g, double guiMouseX, double guiMouseY, float delta) {
        if (g.width() != guiWidth || g.height() != guiHeight || g.guiScale() != guiScale) {
            guiWidth = g.width();
            guiHeight = g.height();
            guiScale = g.guiScale();
            factor = UiScale.factor(guiScale, guiHeight);
            width = (int) Math.floor(guiWidth / factor);
            height = (int) Math.floor(guiHeight / factor);
            init();
        }
        lastMouseX = (float) (guiMouseX / factor);
        lastMouseY = (float) (guiMouseY / factor);
        g.push();
        g.scale(factor, factor);
        render(g, lastMouseX, lastMouseY, delta);
        if (focused != null) {
            focused.renderOverlay(g, lastMouseX, lastMouseY);
        }
        g.pop();
        Meridian.get().renderOverlay(g);
    }

    public final boolean onMouseClicked(double guiX, double guiY, int button) {
        float x = (float) (guiX / factor);
        float y = (float) (guiY / factor);
        if (focused != null) {
            if (focused.captureClicks()) {
                focused.mouseClicked(x, y, button);
                return true;
            }
            if (!focused.contains(x, y)) {
                setFocus(null);
            }
        }
        return mouseClicked(x, y, button);
    }

    public final boolean onMouseReleased(double guiX, double guiY, int button) {
        float x = (float) (guiX / factor);
        float y = (float) (guiY / factor);
        if (focused != null && focused.captureClicks()) {
            focused.mouseReleased(x, y, button);
            return true;
        }
        return mouseReleased(x, y, button);
    }

    public final boolean onMouseDragged(double guiX, double guiY, int button) {
        float x = (float) (guiX / factor);
        float y = (float) (guiY / factor);
        if (focused != null && focused.captureClicks()) {
            focused.mouseDragged(x, y);
            return true;
        }
        return mouseDragged(x, y, button);
    }

    public final boolean onMouseScrolled(double guiX, double guiY, double amount) {
        if (focused != null && focused.captureClicks()) {
            return true;
        }
        return mouseScrolled((float) (guiX / factor), (float) (guiY / factor), amount);
    }

    /** Key press. Returns true when handled. Escape closes the screen unless a widget consumes it. */
    public final boolean onKeyPressed(Key key, boolean ctrl, boolean shift) {
        if (focused != null && focused.keyPressed(key, ctrl, shift)) {
            return true;
        }
        if (keyPressed(key, ctrl, shift)) {
            return true;
        }
        if (key == Key.ESCAPE) {
            close();
            return true;
        }
        return false;
    }

    public final boolean onCharTyped(char c) {
        if (focused != null) {
            return focused.charTyped(c);
        }
        return charTyped(c);
    }

    /** Called by the adapter when the native screen is removed (closed or replaced). */
    public final void onRemoved() {
        setFocus(null);
        removed();
        Meridian.get().config().markDirty();
    }

    // ------------------------------------------------------------------ subclass API (Meridian units)

    /** (Re)builds layout; called on first frame and whenever the window size or GUI scale changes. */
    protected void init() {
    }

    protected abstract void render(Gfx g, float mouseX, float mouseY, float delta);

    protected boolean mouseClicked(float mouseX, float mouseY, int button) {
        return false;
    }

    protected boolean mouseReleased(float mouseX, float mouseY, int button) {
        return false;
    }

    protected boolean mouseDragged(float mouseX, float mouseY, int button) {
        return false;
    }

    protected boolean mouseScrolled(float mouseX, float mouseY, double amount) {
        return false;
    }

    protected boolean keyPressed(Key key, boolean ctrl, boolean shift) {
        return false;
    }

    /** Character typed while no widget has focus. */
    protected boolean charTyped(char c) {
        return false;
    }

    protected void removed() {
    }

    /** Exact (unrounded) width of the virtual space; matches the HUD space. */
    protected final float exactWidth() {
        return (float) (guiWidth / factor);
    }

    protected final float exactHeight() {
        return (float) (guiHeight / factor);
    }

    /** Whether singleplayer pauses while this screen is open. */
    public boolean pausesGame() {
        return false;
    }

    /** Returns to the parent screen (or the game). */
    public void close() {
        Meridian.get().platform().openScreen(parent);
    }

    // ------------------------------------------------------------------ helpers

    public final void setFocus(Widget widget) {
        if (focused == widget) {
            return;
        }
        Widget previous = focused;
        focused = widget;
        if (previous != null) {
            previous.focusLost();
        }
    }

    public final Widget focused() {
        return focused;
    }

    public final int width() {
        return width;
    }

    public final int height() {
        return height;
    }

    public final float mouseX() {
        return lastMouseX;
    }

    public final float mouseY() {
        return lastMouseY;
    }

    /** Dimmed backdrop with the seasonal atmosphere (violet haze, fog) when the theme enables it. */
    protected void renderBackdrop(Gfx g) {
        Theme theme = Theme.current();
        g.fill(0, 0, width, height, theme.backdrop);
        // soft violet light from the bottom — the "fog"
        g.gradient(0, height / 2, width, height, 0x00000000, Colors.withAlpha(theme.accent, theme.decorations ? 0x2C : 0x16));
        if (theme.decorations) {
            // a barely visible seasonal glow on the horizon (warm for Halloween, icy for Christmas)
            g.gradient(0, height * 3 / 4, width, height, 0x00000000, Colors.withAlpha(theme.seasonal, 0x10));
            if (theme.snow()) {
                renderSnow(g);
            }
        }
    }

    private static final int SNOWFLAKES = 70;

    /** Slow snowfall: every flake follows a fixed path derived from its index, so nothing is stored. */
    private void renderSnow(Gfx g) {
        double seconds = Anim.enabled() ? (System.nanoTime() / 1e9) : 0;
        for (int i = 0; i < SNOWFLAKES; i++) {
            double r1 = hash(i * 12.9898);
            double r2 = hash(i * 78.233);
            double r3 = hash(i * 37.719);
            double speed = 6 + r2 * 10;
            float y = (float) ((seconds * speed + r3 * (height + 10)) % (height + 10)) - 5;
            float x = (float) (r1 * width + Math.sin(seconds * 0.6 + i) * 6);
            int size = r2 > 0.7 ? 2 : 1;
            int alpha = 0x30 + (int) (r3 * 0x60);
            g.fill(Math.round(x), Math.round(y), Math.round(x) + size, Math.round(y) + size, (alpha << 24) | 0xEAF4FB);
        }
    }

    private static double hash(double seed) {
        double value = Math.sin(seed) * 43758.5453;
        return value - Math.floor(value);
    }
}
