package dev.medirian.ui;

/**
 * Medirian renders its UI and HUD in its own "virtual" coordinate space with an integer number of
 * real pixels per unit, independent of Minecraft's GUI scale. This keeps Medirian's layout and
 * text crisp and consistent at every resolution and GUI scale setting.
 */
public final class UiScale {

    private UiScale() {
    }

    /** Real pixels per Medirian unit for a window of {@code realHeight} pixels. */
    public static int pixelsPerUnit(int realHeight) {
        if (realHeight < 600) {
            return 1;
        }
        if (realHeight < 1300) {
            return 2;
        }
        if (realHeight < 2000) {
            return 3;
        }
        return 4;
    }

    /** Factor converting Minecraft GUI units into Medirian units ({@code medirian = gui / factor}). */
    public static float factor(double guiScale, int guiHeight) {
        int realHeight = (int) Math.round(guiHeight * guiScale);
        return (float) (pixelsPerUnit(realHeight) / guiScale);
    }
}
