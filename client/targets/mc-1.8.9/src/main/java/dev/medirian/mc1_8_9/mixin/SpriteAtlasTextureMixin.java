package dev.medirian.mc1_8_9.mixin;

import dev.medirian.platform.Hooks;
import net.minecraft.client.texture.SpriteAtlasTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Settings → Advanced → Rendering: animated textures (water, lava, fire, portals…) can stand still. */
@Mixin(SpriteAtlasTexture.class)
public abstract class SpriteAtlasTextureMixin {

    @Inject(method = "update", at = @At("HEAD"), cancellable = true)
    private void medirian$animate(CallbackInfo ci) {
        if (!Hooks.animateTextures()) {
            ci.cancel();
        }
    }
}
