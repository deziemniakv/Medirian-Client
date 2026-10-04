package dev.meridian.mc1_8_9;

import dev.meridian.module.impl.combat.HealthTagsModule;
import dev.meridian.platform.Hooks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;

/** Health Tags for 1.8.9: the name tag string with health appended (used by NameTagMixin). */
public final class NameTags {

    private NameTags() {
    }

    /** {@code vanilla} plus health in legacy colour codes, or {@code vanilla} when Health Tags do not apply. */
    public static String label(String vanilla, LivingEntity entity) {
        HealthTagsModule tags = entity == null ? null : Hooks.healthTags(entity instanceof PlayerEntity);
        if (tags == null) {
            return vanilla;
        }
        float health = entity.getHealth();
        StringBuilder label = new StringBuilder(vanilla).append(' ')
                .append(legacyColor(HealthTagsModule.color(health, entity.getMaxHealth()))).append(tags.text(health));
        String absorption = tags.absorptionText(entity.getAbsorption());
        if (absorption != null) {
            label.append(" §6").append(absorption);
        }
        return label.toString();
    }

    /** HealthTagsModule's colours are exactly §a, §e and §c. */
    private static String legacyColor(int argb) {
        if (argb == HealthTagsModule.HIGH) {
            return "§a";
        }
        return argb == HealthTagsModule.MEDIUM ? "§e" : "§c";
    }
}
