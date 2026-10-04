package dev.meridian.mc1_21_11.mixin;

import dev.meridian.cosmetics.Cosmetic;
import dev.meridian.mc1_21_11.CosmeticRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Carries the worn cosmetics from extraction to the cosmetics layer. */
@Mixin(AvatarRenderState.class)
public abstract class AvatarRenderStateMixin implements CosmeticRenderState {

    @Unique private Cosmetic meridian$hat;
    @Unique private Cosmetic meridian$wings;
    @Unique private float meridian$wingSpread;
    @Unique private final dev.meridian.cosmetics.emote.EmotePose meridian$pose = new dev.meridian.cosmetics.emote.EmotePose();
    @Unique private boolean meridian$emoting;

    @Override
    public dev.meridian.cosmetics.emote.EmotePose meridian$emote() {
        return meridian$emoting ? meridian$pose : null;
    }

    @Override
    public dev.meridian.cosmetics.emote.EmotePose meridian$emoteBuffer() {
        return meridian$pose;
    }

    @Override
    public void meridian$setEmoting(boolean emoting) {
        meridian$emoting = emoting;
    }

    @Override
    public Cosmetic meridian$hat() {
        return meridian$hat;
    }

    @Override
    public Cosmetic meridian$wings() {
        return meridian$wings;
    }

    @Override
    public float meridian$wingSpread() {
        return meridian$wingSpread;
    }

    @Override
    public void meridian$setCosmetics(Cosmetic hat, Cosmetic wings, float wingSpread) {
        meridian$hat = hat;
        meridian$wings = wings;
        meridian$wingSpread = wingSpread;
    }
}
