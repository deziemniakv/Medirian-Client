package dev.medirian.mc26_3.mixin;

import dev.medirian.platform.Hooks;
import net.minecraft.client.renderer.texture.TextureAtlas;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Settings → Advanced → Rendering: animated textures (water, lava, fire, portals…) can stand still. */
@Mixin(TextureAtlas.class)
public abstract class TextureAtlasMixin {

    @Inject(method = "cycleAnimationFrames", at = @At("HEAD"), cancellable = true)
    private void medirian$animate(CallbackInfo ci) {
        if (!Hooks.animateTextures()) {
            ci.cancel();
        }
    }
}
