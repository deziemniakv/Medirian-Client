package dev.medirian.module.impl.combat;

import dev.medirian.module.Category;
import dev.medirian.module.Module;
import dev.medirian.platform.Capability;
import dev.medirian.setting.BooleanSetting;
import dev.medirian.setting.ModeSetting;

/**
 * Health after player name tags ("Steve 9.5❤ +2"), coloured by how much is left. Uses the health
 * the server sends for every living entity; servers that hide it show full health.
 */
public final class HealthTagsModule extends Module {

    public enum Unit implements ModeSetting.Labeled {
        HEARTS("10 ❤"),
        HEALTH("20 HP");

        private final String label;

        Unit(String label) {
            this.label = label;
        }

        @Override
        public String label() {
            return label;
        }
    }

    /** The same colours as Minecraft's §a, §e, §c and §6, so both versions look identical. */
    public static final int HIGH = 0xFF55FF55;
    public static final int MEDIUM = 0xFFFFFF55;
    public static final int LOW = 0xFFFF5555;
    public static final int ABSORPTION = 0xFFFFAA00;

    private final ModeSetting<Unit> unit;
    private final BooleanSetting absorption;
    private final BooleanSetting mobs;

    public HealthTagsModule() {
        super("healthtags", "Health Tags", Category.COMBAT, "Shows health next to player name tags.");
        requires(Capability.HEALTH_TAGS);
        unit = add(new ModeSetting<Unit>("unit", "Show as", Unit.HEARTS));
        absorption = add(new BooleanSetting("absorption", "Absorption", true)
                .description("Golden hearts (golden apples) as +N."));
        mobs = add(new BooleanSetting("mobs", "Also on named mobs", false));
    }

    public boolean appliesTo(boolean isPlayer) {
        return isEnabled() && (isPlayer || mobs.on());
    }

    /** "9.5❤" or "19 HP". */
    public String text(float health) {
        return unit.is(Unit.HEARTS) ? format(health / 2f) + "❤" : format(health) + " HP";
    }

    /** "+2", or null when there is no absorption or it is not shown. */
    public String absorptionText(float amount) {
        if (!absorption.on() || amount <= 0) {
            return null;
        }
        return "+" + format(unit.is(Unit.HEARTS) ? amount / 2f : amount);
    }

    public static int color(float health, float maxHealth) {
        float ratio = maxHealth > 0 ? health / maxHealth : 1f;
        return ratio > 0.6f ? HIGH : ratio > 0.3f ? MEDIUM : LOW;
    }

    /** One decimal at most, without a trailing ".0". */
    public static String format(float value) {
        float rounded = Math.round(value * 10f) / 10f;
        return rounded == (int) rounded ? String.valueOf((int) rounded) : String.valueOf(rounded);
    }
}
