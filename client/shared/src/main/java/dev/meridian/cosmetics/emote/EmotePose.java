package dev.meridian.cosmetics.emote;

import java.util.Arrays;

/**
 * Rotations an emote gives the player model's parts at one moment (radians, the game's model
 * axes). Parts the emote leaves alone are NaN. {@link #weight} fades the pose in and out: adapters
 * blend {@code vanilla + (emote - vanilla) * weight}.
 */
public final class EmotePose {

    public static final int RIGHT_ARM = 0;
    public static final int LEFT_ARM = 1;
    public static final int RIGHT_LEG = 2;
    public static final int LEFT_LEG = 3;
    public static final int HEAD = 4;
    public static final int BODY = 5;
    public static final int PARTS = 6;

    /** x, y, z rotation per part. */
    public final float[] rotations = new float[PARTS * 3];
    public float weight;

    public EmotePose() {
        clear();
    }

    public void clear() {
        Arrays.fill(rotations, Float.NaN);
        weight = 0f;
    }

    public void set(int part, float x, float y, float z) {
        rotations[part * 3] = x;
        rotations[part * 3 + 1] = y;
        rotations[part * 3 + 2] = z;
    }

    public boolean has(int part) {
        return !Float.isNaN(rotations[part * 3]);
    }

    /** The blended rotation of one axis of a part, given the game's value. */
    public float blend(int part, int axis, float vanilla) {
        float target = rotations[part * 3 + axis];
        return Float.isNaN(target) ? vanilla : vanilla + (target - vanilla) * weight;
    }

    public void copyFrom(EmotePose other) {
        System.arraycopy(other.rotations, 0, rotations, 0, rotations.length);
        weight = other.weight;
    }
}
