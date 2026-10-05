package dev.medirian.mc26_3.mixin;

import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Access to the protected {@code addLayer}. */
@Mixin(LivingEntityRenderer.class)
public interface LivingEntityRendererInvoker {

    @SuppressWarnings("rawtypes")
    @Invoker("addLayer")
    boolean medirian$addLayer(RenderLayer layer);
}
