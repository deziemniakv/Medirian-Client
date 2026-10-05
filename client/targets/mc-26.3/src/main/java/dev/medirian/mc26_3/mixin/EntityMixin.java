package dev.medirian.mc26_3.mixin;

import dev.medirian.perf.CullState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Stores Entity Culling's occlusion result on each entity. */
@Mixin(Entity.class)
public abstract class EntityMixin implements CullState {

    @Unique private long medirian$checkedAt;
    @Unique private boolean medirian$visible;

    @Override
    public long medirian$cullCheckedAt() {
        return medirian$checkedAt;
    }

    @Override
    public boolean medirian$cullVisible() {
        return medirian$visible;
    }

    @Override
    public void medirian$setCull(boolean visible, long checkedAt) {
        medirian$visible = visible;
        medirian$checkedAt = checkedAt;
    }
}
