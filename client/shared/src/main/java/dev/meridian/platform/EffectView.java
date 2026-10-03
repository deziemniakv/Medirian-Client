package dev.meridian.platform;

/** Read-only view of an active status effect. */
public interface EffectView {

    /** Localised effect name without the level, e.g. "Speed". */
    String name();

    /** 0-based amplifier (0 = level I). */
    int amplifier();

    /** Remaining duration in ticks. */
    int durationTicks();

    boolean infinite();

    boolean beneficial();

    /** Native effect object, only for the adapter's own rendering code. */
    Object handle();
}
