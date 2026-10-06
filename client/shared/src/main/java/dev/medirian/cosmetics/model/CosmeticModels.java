package dev.medirian.cosmetics.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.medirian.core.Log;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

/**
 * Meshes of worn cosmetics. Hats are boxes described in {@code /medirian/cosmetics/models/<id>.json}
 * (the same files the texture generator paints); wings are two textured planes on the back whose
 * spread is animated every frame.
 *
 * <p>Hat boxes are in head space (model pixels from the neck pivot: the head spans y -8..0 and
 * x/z -4..4) and are authored resting on top of the head; {@code seat} then sinks the whole hat
 * that many pixels so it sits down over the forehead like a real hat. Every box that reaches into
 * the head wraps it together with the skin's hat layer ({@link #fits}), so no part of the head
 * pokes through, whatever the skin.
 */
public final class CosmeticModels {

    /** The head's half width, its top and how far the skin's hat layer stands out (head space). */
    public static final float HEAD_HALF = 4f;
    public static final float HEAD_TOP = -8f;
    public static final float HAT_LAYER = 0.5f;

    private static final Map<String, CosmeticMesh> CACHE = new HashMap<String, CosmeticMesh>();
    private static final CosmeticMesh EMPTY = new MeshBuilder(1, 1).build();

    /** Size of one wing in model pixels and where it starts on the back (body part space). */
    static final float WING_WIDTH = 16f;
    static final float WING_HEIGHT = 16f;
    static final float WING_TOP = -1f;
    static final float WING_ROOT_X = 1.5f;
    static final float WING_ROOT_Z = 2.2f;

    private CosmeticModels() {
    }

    /** The mesh of a box model (hats), or an empty mesh when the model is missing. */
    public static synchronized CosmeticMesh model(String id) {
        CosmeticMesh mesh = CACHE.get(id);
        if (mesh == null) {
            mesh = load(id);
            CACHE.put(id, mesh);
        }
        return mesh;
    }

    /**
     * Whether a (seated) hat box sits right on the head: it either wraps the head and its hat
     * layer on all four sides, or stays completely outside them.
     */
    public static boolean fits(float x0, float y0, float z0, float x1, float y1, float z1) {
        float outer = HEAD_HALF + HAT_LAYER;
        float wrap = outer + 0.05f;
        boolean outside = y1 <= HEAD_TOP - HAT_LAYER || x1 <= -outer || x0 >= outer || z1 <= -outer || z0 >= outer;
        boolean wraps = x0 <= -wrap && x1 >= wrap && z0 <= -wrap && z1 >= wrap;
        return outside || wraps;
    }

    static CosmeticMesh parse(JsonObject json) {
        JsonArray texture = json.getAsJsonArray("texture");
        MeshBuilder builder = new MeshBuilder(texture.get(0).getAsInt(), texture.get(1).getAsInt());
        float seat = json.has("seat") ? json.get("seat").getAsFloat() : 0f;
        for (JsonElement element : json.getAsJsonArray("boxes")) {
            JsonObject box = element.getAsJsonObject();
            float[] from = floats(box.getAsJsonArray("from"));
            from[1] += seat;
            float[] size = floats(box.getAsJsonArray("size"));
            float[] uv = floats(box.getAsJsonArray("uv"));
            float[] uvSize = box.has("uvSize") ? floats(box.getAsJsonArray("uvSize")) : size;
            builder.box(from[0], from[1], from[2], size[0], size[1], size[2], (int) uv[0], (int) uv[1],
                    uvSize[0], uvSize[1], uvSize[2]);
        }
        return builder.build();
    }

    private static float[] floats(JsonArray array) {
        float[] values = new float[array.size()];
        for (int i = 0; i < values.length; i++) {
            values[i] = array.get(i).getAsFloat();
        }
        return values;
    }

    private static CosmeticMesh load(String id) {
        InputStream in = CosmeticModels.class.getResourceAsStream("/medirian/cosmetics/models/" + id + ".json");
        if (in == null) {
            Log.warn("Cosmetic model {} is missing", id);
            return EMPTY;
        }
        try {
            return parse(new JsonParser().parse(new InputStreamReader(in, Charset.forName("UTF-8"))).getAsJsonObject());
        } catch (RuntimeException e) {
            Log.error("Cosmetic model {} is invalid", id, e);
            return EMPTY;
        } finally {
            try {
                in.close();
            } catch (java.io.IOException ignored) {
                // read already
            }
        }
    }

    /**
     * Two wings on the back, in body part space. The texture holds the left wing in its left half;
     * the right wing uses it mirrored. {@code spreadDegrees} turns each wing backwards around the
     * spine (0 = flat against the back plane, sideways).
     */
    public static CosmeticMesh wings(float spreadDegrees) {
        double angle = Math.toRadians(spreadDegrees);
        float cos = (float) Math.cos(angle);
        float sin = (float) Math.sin(angle);
        MeshBuilder builder = new MeshBuilder(64, 32);
        for (int side = -1; side <= 1; side += 2) {
            float rootX = side * WING_ROOT_X;
            float tipX = rootX + side * WING_WIDTH * cos;
            float tipZ = WING_ROOT_Z + WING_WIDTH * sin;
            float top = WING_TOP;
            float bottom = WING_TOP + WING_HEIGHT;
            // normal of the plane, pointing away from the back
            float nx = -side * sin;
            float nz = cos;
            // left wing (+x): u runs from the root (u = 32) to the tip (u = 0); the right wing mirrors it
            float uRoot = 32f / 64f;
            float uTip = 0f;
            builder.quad(new float[] {
                rootX, top, WING_ROOT_Z, uRoot, 0f,
                tipX, top, tipZ, uTip, 0f,
                tipX, bottom, tipZ, uTip, 1f,
                rootX, bottom, WING_ROOT_Z, uRoot, 1f,
                nx, 0f, nz
            });
        }
        return builder.build();
    }

    /**
     * Wing spread for an animation time in seconds: a slow beat while standing, a faster and
     * wider one while moving fast or flying. Each player gets a phase from their id.
     */
    public static float wingSpread(double seconds, boolean fast, int phaseSeed) {
        double speed = fast ? 6.0 : 2.2;
        double amplitude = fast ? 28.0 : 12.0;
        double phase = (phaseSeed & 0xFFFF) / 65535.0 * Math.PI * 2;
        return (float) (24.0 + amplitude * (0.5 + 0.5 * Math.sin(seconds * speed + phase)));
    }
}
