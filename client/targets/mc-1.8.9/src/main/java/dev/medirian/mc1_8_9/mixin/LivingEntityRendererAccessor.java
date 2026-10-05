package dev.medirian.mc1_8_9.mixin;

import net.minecraft.client.render.entity.LivingEntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/** Access to the feature renderer list. */
@Mixin(LivingEntityRenderer.class)
public interface LivingEntityRendererAccessor {

    @SuppressWarnings("rawtypes")
    @Accessor("features")
    List medirian$features();
}
