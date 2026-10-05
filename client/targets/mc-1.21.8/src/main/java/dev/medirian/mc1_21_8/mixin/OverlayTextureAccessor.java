package dev.medirian.mc1_21_8.mixin;

import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Access to the overlay texture whose upper half is the hurt colour (Hit Color). */
@Mixin(OverlayTexture.class)
public interface OverlayTextureAccessor {

    @Accessor("texture")
    DynamicTexture medirian$texture();
}
