package dev.meridian.mc1_8_9.mixin;

import dev.meridian.mc1_8_9.LegacyCosmetics;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hats and wings: adds the cosmetics feature renderer to the player renderer. */
@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerEntityRendererMixin {

    @Inject(method = "<init>(Lnet/minecraft/client/render/entity/EntityRenderDispatcher;Z)V", at = @At("TAIL"))
    @SuppressWarnings("unchecked")
    private void meridian$addFeature(EntityRenderDispatcher dispatcher, boolean slim, CallbackInfo ci) {
        ((LivingEntityRendererAccessor) this).meridian$features().add(new LegacyCosmetics.Feature((PlayerEntityRenderer) (Object) this));
    }
}
