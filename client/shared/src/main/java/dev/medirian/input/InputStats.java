package dev.medirian.input;

/**
 * Always-on click statistics. Recording costs a few array writes per click, so it runs even when
 * no CPS module is enabled — Keystrokes and CPS read from the same source.
 */
public final class InputStats {

    private final ClickTracker left = new ClickTracker();
    private final ClickTracker right = new ClickTracker();

    public void click(int button, long nowMs) {
        if (button == 0) {
            left.click(nowMs);
        } else if (button == 1) {
            right.click(nowMs);
        }
    }

    public ClickTracker left() {
        return left;
    }

    public ClickTracker right() {
        return right;
    }
}
