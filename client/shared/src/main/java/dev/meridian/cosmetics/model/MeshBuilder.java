package dev.meridian.cosmetics.model;

import java.util.Arrays;

/** Builds a {@link CosmeticMesh} from boxes (Minecraft's box UV layout) and free quads. */
public final class MeshBuilder {

    private final float texWidth;
    private final float texHeight;
    private float[] data = new float[CosmeticMesh.STRIDE * 16];
    private int quads;

    public MeshBuilder(int texWidth, int texHeight) {
        this.texWidth = texWidth;
        this.texHeight = texHeight;
    }

    /**
     * A box from ({@code x}, {@code y}, {@code z}) with size {@code w}×{@code h}×{@code d}, textured
     * like a vanilla model cube with texture offset ({@code u}, {@code v}): the top and bottom
     * faces in the first row, then the right, front, left and back faces.
     */
    public MeshBuilder box(float x, float y, float z, float w, float h, float d, int u, int v) {
        return box(x, y, z, w, h, d, u, v, w, h, d);
    }

    /** A box whose texture layout uses other (whole-pixel) sizes than its geometry. */
    public MeshBuilder box(float x, float y, float z, float w, float h, float d, int u, int v, float uw, float uh, float ud) {
        float x2 = x + w;
        float y2 = y + h;
        float z2 = z + d;
        // top (y = y, facing up = -y)
        face(x, y, z2, x2, y, z2, x2, y, z, x, y, z, u + ud, v, uw, ud, 0, -1, 0);
        // bottom (facing +y)
        face(x, y2, z, x2, y2, z, x2, y2, z2, x, y2, z2, u + ud + uw, v, uw, ud, 0, 1, 0);
        // right side of the wearer (-x)
        face(x, y, z2, x, y, z, x, y2, z, x, y2, z2, u, v + ud, ud, uh, -1, 0, 0);
        // front (-z)
        face(x, y, z, x2, y, z, x2, y2, z, x, y2, z, u + ud, v + ud, uw, uh, 0, 0, -1);
        // left side (+x)
        face(x2, y, z, x2, y, z2, x2, y2, z2, x2, y2, z, u + ud + uw, v + ud, ud, uh, 1, 0, 0);
        // back (+z)
        face(x2, y, z2, x, y, z2, x, y2, z2, x2, y2, z2, u + ud + uw + ud, v + ud, uw, uh, 0, 0, 1);
        return this;
    }

    /**
     * A quad given by its corners in drawing order (top-left, top-right, bottom-right,
     * bottom-left of the texture region at ({@code u}, {@code v}) sized {@code uw}×{@code vh} pixels).
     */
    public MeshBuilder face(float x1, float y1, float z1, float x2, float y2, float z2,
                            float x3, float y3, float z3, float x4, float y4, float z4,
                            float u, float v, float uw, float vh, float nx, float ny, float nz) {
        float u0 = u / texWidth;
        float v0 = v / texHeight;
        float u1 = (u + uw) / texWidth;
        float v1 = (v + vh) / texHeight;
        return quad(new float[] {
            x1, y1, z1, u0, v0,
            x2, y2, z2, u1, v0,
            x3, y3, z3, u1, v1,
            x4, y4, z4, u0, v1,
            nx, ny, nz
        });
    }

    /** A quad from {@link CosmeticMesh#STRIDE} floats (texture coordinates already normalised). */
    public MeshBuilder quad(float[] quad) {
        if ((quads + 1) * CosmeticMesh.STRIDE > data.length) {
            data = Arrays.copyOf(data, data.length * 2);
        }
        System.arraycopy(quad, 0, data, quads * CosmeticMesh.STRIDE, CosmeticMesh.STRIDE);
        quads++;
        return this;
    }

    public CosmeticMesh build() {
        return new CosmeticMesh(Arrays.copyOf(data, quads * CosmeticMesh.STRIDE), quads);
    }
}
