package dev.medirian.perf;

/**
 * Per-entity occlusion result, stored on the entity itself by the version adapter (a mixin
 * implements this interface on Minecraft's entity class), so no lookup table is needed and the
 * state disappears with the entity.
 */
public interface CullState {

    /** {@link System#nanoTime()} of the last occlusion test, or 0 when never tested. */
    long medirian$cullCheckedAt();

    boolean medirian$cullVisible();

    void medirian$setCull(boolean visible, long checkedAt);
}
