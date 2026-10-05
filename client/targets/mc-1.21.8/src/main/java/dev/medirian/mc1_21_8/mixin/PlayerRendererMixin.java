package dev.medirian.mc1_21_8.mixin;

import dev.medirian.mc1_21_8.ModernCosmetics;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hats and wings: the cosmetics layer, and the worn cosmetics resolved into the render state. */
@Mixin(PlayerRenderer.class)
public abstract class PlayerRendererMixin {

    @Inject(method = "<init>", at = @At("TAIL"))
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void medirian$addLayer(EntityRendererProvider.Context context, boolean slim, CallbackInfo ci) {
        ((LivingEntityRendererInvoker) this).medirian$addLayer(new ModernCosmetics.Layer((PlayerRenderer) (Object) this));
    }

    @Inject(method = "extractRenderState(Lnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/client/renderer/entity/state/PlayerRenderState;F)V",
            at = @At("TAIL"))
    private void medirian$cosmetics(AbstractClientPlayer player, PlayerRenderState state, float partialTick, CallbackInfo ci) {
        ModernCosmetics.extract(player, state, partialTick);
    }
}
