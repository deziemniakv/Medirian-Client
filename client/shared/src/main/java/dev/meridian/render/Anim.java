package dev.meridian.render;

/**
 * Frame-rate independent animation value. Approaches its target exponentially; when animations
 * are disabled in settings it snaps immediately (performance over effects).
 */
public final class Anim {

    private static volatile boolean enabled = true;

    private float value;
    private float target;
    private long lastNanos;
    private final float speed;

    /** @param speed approach rate per second; 12 settles in roughly 0.25 s */
    public Anim(float initial, float speed) {
        this.value = initial;
        this.target = initial;
        this.speed = speed;
    }

    public static void setEnabled(boolean on) {
        enabled = on;
    }

    public static boolean enabled() {
        return enabled;
    }

    public Anim target(float newTarget) {
        this.target = newTarget;
        return this;
    }

    public void snap(float v) {
        value = v;
        target = v;
    }

    /** Advances the animation and returns the current value. */
    public float get() {
        long now = System.nanoTime();
        if (!enabled) {
            value = target;
        } else if (lastNanos != 0 && value != target) {
            float dt = Math.min(0.1f, (now - lastNanos) / 1_000_000_000f);
            value += (target - value) * (1f - (float) Math.exp(-speed * dt));
            if (Math.abs(target - value) < 0.001f) {
                value = target;
            }
        }
        lastNanos = now;
        return value;
    }
}
