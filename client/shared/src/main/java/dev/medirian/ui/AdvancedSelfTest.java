package dev.medirian.ui;

import dev.medirian.core.Log;
import dev.medirian.core.Medirian;
import dev.medirian.platform.VanillaOptions;
import dev.medirian.platform.VanillaOptions.Option;

import java.util.EnumSet;
import java.util.Set;

/** Self-test checks of Settings → Advanced, shared by the version adapters' self-tests. */
public final class AdvancedSelfTest {

    /** Options not flipped by the check: they resize the window, reload textures or rebuild every chunk. */
    private static final Set<Option> HEAVY = EnumSet.of(Option.FULLSCREEN, Option.GUI_SCALE, Option.MIPMAP, Option.GRAPHICS,
            Option.IMPROVED_TRANSPARENCY, Option.CUTOUT_LEAVES, Option.RENDER_DISTANCE, Option.SIMULATION_DISTANCE, Option.BIOME_BLEND);

    private AdvancedSelfTest() {
    }

    /**
     * Changes every light Minecraft option through {@link VanillaOptions} and back, checking that
     * Minecraft really took the value. Logs "Self-test FAILED" otherwise.
     */
    public static void verifyVanillaOptions(Medirian medirian) {
        VanillaOptions vanilla = medirian.platform().vanillaOptions();
        if (vanilla == null) {
            Log.error("Self-test FAILED: Settings → Advanced has no Minecraft options in this version");
            return;
        }
        int checked = 0;
        int absent = 0;
        for (Option option : Option.values()) {
            VanillaOptions.Spec spec = vanilla.spec(option);
            if (spec == null) {
                absent++;
                continue;
            }
            if (HEAVY.contains(option)) {
                continue;
            }
            int before = vanilla.get(option);
            int other = before == spec.min ? Math.min(spec.max, spec.min + spec.step) : spec.min;
            vanilla.set(option, other);
            int read = vanilla.get(option);
            vanilla.set(option, before);
            if (read != other || vanilla.get(option) != before) {
                Log.error("Self-test FAILED: Minecraft option {} did not change through Settings → Advanced ({} -> {}, read {})",
                        option, before, other, read);
                return;
            }
            checked++;
        }
        Log.info("Self-test: Settings → Advanced changes Minecraft options OK ({} checked, {} not in this version)", checked, absent);
    }
}
