package dev.meridian.mc1_21_8.mixin;

import dev.meridian.perf.CullState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Stores Entity Culling's occlusion result on each entity. */
@Mixin(Entity.class)
public abstract class EntityMixin implements CullState {

    @Unique private long meridian$checkedAt;
    @Unique private boolean meridian$visible;

    @Override
    public long meridian$cullCheckedAt() {
        return meridian$checkedAt;
    }

    @Override
    public boolean meridian$cullVisible() {
        return meridian$visible;
    }

    @Override
    public void meridian$setCull(boolean visible, long checkedAt) {
        meridian$visible = visible;
        meridian$checkedAt = checkedAt;
    }
}
