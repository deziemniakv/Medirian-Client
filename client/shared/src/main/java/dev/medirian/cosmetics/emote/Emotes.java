package dev.medirian.cosmetics.emote;

/**
 * The emote animations: each is a function of the time since it started. Angles are in radians
 * on the game's model axes (a negative x rotation raises an arm forwards and up; a positive z
 * rotation moves the right arm outwards).
 */
public final class Emotes {

    private static final double FADE = 0.2;

    private Emotes() {
    }

    /** Length of an emote in seconds (0 for an unknown one). */
    public static double duration(String id) {
        switch (id) {
            case "emote_wave":
                return 2.4;
            case "emote_cheer":
                return 2.0;
            case "emote_dance":
                return 4.0;
            default:
                return 0;
        }
    }

    /** Writes the pose of {@code id} at {@code t} seconds into {@code pose}; false once it has ended. */
    public static boolean pose(String id, double t, EmotePose pose) {
        pose.clear();
        double length = duration(id);
        if (length <= 0 || t < 0 || t >= length) {
            return false;
        }
        pose.weight = (float) Math.min(1.0, Math.min(t / FADE, (length - t) / FADE));
        float s;
        switch (id) {
            case "emote_wave":
                // right arm up, waving from side to side
                s = (float) Math.sin(t * 12);
                pose.set(EmotePose.RIGHT_ARM, -2.6f, 0f, 0.25f + 0.35f * s);
                pose.set(EmotePose.HEAD, 0f, 0.15f, 0f);
                break;
            case "emote_cheer":
                // both arms up, bouncing
                s = (float) Math.sin(t * 14);
                pose.set(EmotePose.RIGHT_ARM, -2.9f + 0.15f * s, 0f, 0.35f);
                pose.set(EmotePose.LEFT_ARM, -2.9f + 0.15f * s, 0f, -0.35f);
                pose.set(EmotePose.HEAD, -0.3f, 0f, 0f);
                break;
            case "emote_dance":
                s = (float) Math.sin(t * 7);
                float sway = (float) Math.sin(t * 3.5);
                pose.set(EmotePose.RIGHT_ARM, 1.2f * s, 0f, 0.45f + 0.2f * (float) Math.sin(t * 14));
                pose.set(EmotePose.LEFT_ARM, -1.2f * s, 0f, -0.45f - 0.2f * (float) Math.sin(t * 14));
                pose.set(EmotePose.RIGHT_LEG, 0.5f * s, 0f, 0f);
                pose.set(EmotePose.LEFT_LEG, -0.5f * s, 0f, 0f);
                pose.set(EmotePose.HEAD, 0f, 0.4f * sway, 0f);
                pose.set(EmotePose.BODY, 0f, 0.25f * sway, 0f);
                break;
            default:
                return false;
        }
        return true;
    }
}
