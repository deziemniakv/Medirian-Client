package dev.meridian.perf;

/**
 * Per-entity occlusion result, stored on the entity itself by the version adapter (a mixin
 * implements this interface on Minecraft's entity class), so no lookup table is needed and the
 * state disappears with the entity.
 */
public interface CullState {

    /** {@link System#nanoTime()} of the last occlusion test, or 0 when never tested. */
    long meridian$cullCheckedAt();

    boolean meridian$cullVisible();

    void meridian$setCull(boolean visible, long checkedAt);
}
