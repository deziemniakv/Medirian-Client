package dev.meridian.platform;

/** Block opacity for occlusion tests. */
public interface Occluders {

    /**
     * Whether the block at the given position is a full, opaque cube that hides everything behind
     * it. Transparent and partial blocks (glass, leaves, slabs, fences…) and unloaded chunks are
     * not occluders. Called many times per frame on the render thread: must be fast.
     */
    boolean isOccluder(int x, int y, int z);
}
