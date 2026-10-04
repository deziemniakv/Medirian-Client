package dev.meridian.mc26_3;

import dev.meridian.platform.EntityView;
import net.minecraft.world.entity.LivingEntity;

/** Reuses the view of the most recent attack target to avoid allocating on every hit. */
public final class AttackTargets {

    private static Views.Entity last;

    private AttackTargets() {
    }

    public static EntityView view(LivingEntity entity) {
        if (last == null || last.entity != entity) {
            last = new Views.Entity(entity);
        }
        return last;
    }
}
