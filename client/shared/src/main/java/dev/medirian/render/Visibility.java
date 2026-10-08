package dev.medirian.render;

import dev.medirian.config.ProfileSettings;
import dev.medirian.setting.NumberSetting;

/**
 * The visibility distances of Settings → Advanced → Visibility: what is drawn how far from the
 * camera. Every check is one multiplication and a comparison, so it runs in render loops.
 */
public final class Visibility {

    /** Kinds of entities with their own distance. */
    public static final int PLAYER = 0;
    public static final int ITEM = 1;
    public static final int OTHER = 2;

    private static ProfileSettings settings;

    private Visibility() {
    }

    public static void bind(ProfileSettings profileSettings) {
        settings = profileSettings;
    }

    /** Whether an entity {@code distanceSq} blocks² from the camera is drawn. */
    public static boolean entity(int kind, double distanceSq) {
        if (settings == null) {
            return true;
        }
        return within(kind == PLAYER ? settings.playerDistance : kind == ITEM ? settings.itemDistance : settings.entityDistance, distanceSq);
    }

    public static boolean blockEntity(double distanceSq) {
        return settings == null || within(settings.blockEntityDistance, distanceSq);
    }

    public static boolean particle(double distanceSq) {
        return settings == null || within(settings.particleDistance, distanceSq);
    }

    public static boolean nameTag(double distanceSq) {
        return settings == null || within(settings.nameTagDistance, distanceSq);
    }

    public static boolean waypoint(double distanceSq) {
        return settings == null || within(settings.waypointDistance, distanceSq);
    }

    public static boolean cosmetics(double distanceSq) {
        return settings == null || within(settings.cosmeticDistance, distanceSq);
    }

    static boolean within(NumberSetting setting, double distanceSq) {
        if (setting.atMax()) {
            return true;
        }
        double limit = setting.doubleValue();
        return distanceSq <= limit * limit;
    }
}
