package dev.meridian.mc26_3;

/**
 * Which mouse buttons are held. 26.x has no GLFW to ask, so the state is recorded from the
 * game's own button events ({@code MouseHandler#onButton}).
 */
public final class MouseButtons {

    private static final boolean[] DOWN = new boolean[16];

    private MouseButtons() {
    }

    public static void set(int button, boolean down) {
        if (button >= 0 && button < DOWN.length) {
            DOWN[button] = down;
        }
    }

    public static boolean isDown(int button) {
        return button >= 0 && button < DOWN.length && DOWN[button];
    }
}
