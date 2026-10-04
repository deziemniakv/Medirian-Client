package dev.meridian.mc26_3.mixin;

import dev.meridian.cosmetics.emote.EmotePose;
import dev.meridian.mc26_3.CosmeticRenderState;
import dev.meridian.mc26_3.ModernCosmetics;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Emotes: override the pose after the game set it up (sleeves and the jacket are child parts and follow). */
@Mixin(PlayerModel.class)
public abstract class PlayerModelMixin {

    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"))
    private void meridian$emote(AvatarRenderState state, CallbackInfo ci) {
        EmotePose pose = ((CosmeticRenderState) state).meridian$emote();
        if (pose != null) {
            ModernCosmetics.applyEmote(pose, (PlayerModel) (Object) this);
        }
    }
}
