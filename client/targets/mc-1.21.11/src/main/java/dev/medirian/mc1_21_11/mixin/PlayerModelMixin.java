package dev.medirian.mc1_21_11.mixin;

import dev.medirian.cosmetics.emote.EmotePose;
import dev.medirian.mc1_21_11.CosmeticRenderState;
import dev.medirian.mc1_21_11.ModernCosmetics;
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
    private void medirian$emote(AvatarRenderState state, CallbackInfo ci) {
        EmotePose pose = ((CosmeticRenderState) state).medirian$emote();
        if (pose != null) {
            ModernCosmetics.applyEmote(pose, (PlayerModel) (Object) this);
        }
    }
}
