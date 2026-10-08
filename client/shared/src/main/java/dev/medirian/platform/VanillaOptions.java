package dev.medirian.platform;

/**
 * Minecraft's own options (options.txt), read and changed through the version adapter exactly as
 * Minecraft's option screens do — with the same side effects (reloading chunks or textures,
 * resizing the window) — so Settings → Advanced can offer them next to Medirian's settings.
 */
public interface VanillaOptions {

    /** The options Medirian shows. Values are ints: a number, a choice index or 0/1 for on/off. */
    enum Option {
        // graphics
        RENDER_DISTANCE, SIMULATION_DISTANCE, ENTITY_DISTANCE, GRAPHICS, SMOOTH_LIGHTING, CLOUDS, PARTICLES, ENTITY_SHADOWS,
        BIOME_BLEND, MIPMAP, VSYNC, FPS_LIMIT, FULLSCREEN, BRIGHTNESS, FOV, VIEW_BOBBING, IMPROVED_TRANSPARENCY,
        WEATHER_RADIUS, CUTOUT_LEAVES, VIGNETTE, CHUNK_FADE, CHUNK_UPDATES, CLOUD_RANGE,
        // interface
        GUI_SCALE, MENU_BLUR, CHAT_OPACITY, CHAT_SCALE, CHAT_WIDTH, TEXT_BACKGROUND, ADVANCED_TOOLTIPS, HELD_ITEM_TOOLTIPS,
        ATTACK_INDICATOR, DAMAGE_TILT, SCREEN_EFFECTS, FOV_EFFECTS
    }

    /** How an option is edited: on/off, one of several choices, or a number range. */
    final class Spec {
        public final int min;
        public final int max;
        public final int step;
        /** Choice labels (English, translated by the screen), or null for numbers and on/off. */
        public final String[] choices;
        public final boolean toggle;
        /** Unit appended to numbers, e.g. "%" or " chunks". */
        public final String unit;
        /** Label of the maximum (e.g. "Unlimited"), or null. */
        public final String maxLabel;
        /** Label of the minimum (e.g. "Off" or "Auto"), or null. */
        public final String minLabel;

        private Spec(int min, int max, int step, String[] choices, boolean toggle, String unit, String minLabel, String maxLabel) {
            this.min = min;
            this.max = max;
            this.step = step;
            this.choices = choices;
            this.toggle = toggle;
            this.unit = unit;
            this.minLabel = minLabel;
            this.maxLabel = maxLabel;
        }

        public static Spec toggle() {
            return new Spec(0, 1, 1, null, true, "", null, null);
        }

        public static Spec choices(String... labels) {
            return new Spec(0, labels.length - 1, 1, labels, false, "", null, null);
        }

        public static Spec range(int min, int max, int step, String unit) {
            return new Spec(min, max, step, null, false, unit, null, null);
        }

        public Spec withMinLabel(String label) {
            return new Spec(min, max, step, choices, toggle, unit, label, maxLabel);
        }

        public Spec withMaxLabel(String label) {
            return new Spec(min, max, step, choices, toggle, unit, minLabel, label);
        }
    }

    /** How this version edits {@code option}, or null when it does not have it. */
    Spec spec(Option option);

    int get(Option option);

    /** Changes the option like Minecraft's own screens do and saves options.txt. */
    void set(Option option, int value);
}
