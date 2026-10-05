package dev.medirian.mc1_21_8.mixin;

import dev.medirian.cosmetics.emote.EmotePose;
import dev.medirian.mc1_21_8.CosmeticRenderState;
import dev.medirian.mc1_21_8.ModernCosmetics;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Emotes: override the pose after the game set it up (sleeves and the jacket are child parts and follow). */
@Mixin(PlayerModel.class)
public abstract class PlayerModelMixin {

    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/PlayerRenderState;)V", at = @At("TAIL"))
    private void medirian$emote(PlayerRenderState state, CallbackInfo ci) {
        EmotePose pose = ((CosmeticRenderState) state).medirian$emote();
        if (pose != null) {
            ModernCosmetics.applyEmote(pose, (PlayerModel) (Object) this);
        }
    }
}
