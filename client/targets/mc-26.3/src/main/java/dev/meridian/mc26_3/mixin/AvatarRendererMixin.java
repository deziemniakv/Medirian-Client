package dev.meridian.mc26_3.mixin;

import dev.meridian.mc26_3.ModernCosmetics;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hats and wings: the cosmetics layer, and the worn cosmetics resolved into the render state. */
@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {

    @Inject(method = "<init>", at = @At("TAIL"))
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void meridian$addLayer(EntityRendererProvider.Context context, boolean slim, CallbackInfo ci) {
        ((LivingEntityRendererInvoker) this).meridian$addLayer(new ModernCosmetics.Layer((AvatarRenderer) (Object) this));
    }

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
            at = @At("TAIL"))
    private void meridian$cosmetics(Avatar player, AvatarRenderState state, float partialTick, CallbackInfo ci) {
        ModernCosmetics.extract(player, state, partialTick);
    }
}
