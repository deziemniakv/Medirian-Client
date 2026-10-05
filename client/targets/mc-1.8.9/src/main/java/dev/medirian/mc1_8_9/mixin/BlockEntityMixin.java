package dev.medirian.mc1_8_9.mixin;

import dev.medirian.perf.CullState;
import net.minecraft.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Stores Entity Culling's occlusion result on each block entity. */
@Mixin(BlockEntity.class)
public abstract class BlockEntityMixin implements CullState {

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
