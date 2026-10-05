package dev.medirian.cosmetics.model;

/**
 * Geometry of a worn cosmetic: textured quads in model pixels (1/16 of a block) on Minecraft's model
 * axes (y points down, +z is behind the player), relative to the body part it is attached to.
 * Version adapters only transform these vertices to the part and hand them to the game.
 *
 * <p>Per quad {@link #STRIDE} floats: four vertices of (x, y, z, u, v) followed by the normal.
 * Texture coordinates are fractions of the texture (0..1).
 */
public final class CosmeticMesh {

    public static final int STRIDE = 23;

    private final float[] data;
    private final int quads;

    CosmeticMesh(float[] data, int quads) {
        this.data = data;
        this.quads = quads;
    }

    public int quads() {
        return quads;
    }

    /** The quad data (do not modify). */
    public float[] data() {
        return data;
    }
}
