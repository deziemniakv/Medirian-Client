package dev.medirian.mc1_21_8.mixin;

import dev.medirian.cosmetics.Cosmetic;
import dev.medirian.mc1_21_8.CosmeticRenderState;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Carries the worn cosmetics from extraction to the cosmetics layer. */
@Mixin(PlayerRenderState.class)
public abstract class PlayerRenderStateMixin implements CosmeticRenderState {

    @Unique private Cosmetic medirian$hat;
    @Unique private Cosmetic medirian$wings;
    @Unique private float medirian$wingSpread;
    @Unique private final dev.medirian.cosmetics.emote.EmotePose medirian$pose = new dev.medirian.cosmetics.emote.EmotePose();
    @Unique private boolean medirian$emoting;

    @Override
    public dev.medirian.cosmetics.emote.EmotePose medirian$emote() {
        return medirian$emoting ? medirian$pose : null;
    }

    @Override
    public dev.medirian.cosmetics.emote.EmotePose medirian$emoteBuffer() {
        return medirian$pose;
    }

    @Override
    public void medirian$setEmoting(boolean emoting) {
        medirian$emoting = emoting;
    }

    @Override
    public Cosmetic medirian$hat() {
        return medirian$hat;
    }

    @Override
    public Cosmetic medirian$wings() {
        return medirian$wings;
    }

    @Override
    public float medirian$wingSpread() {
        return medirian$wingSpread;
    }

    @Override
    public void medirian$setCosmetics(Cosmetic hat, Cosmetic wings, float wingSpread) {
        medirian$hat = hat;
        medirian$wings = wings;
        medirian$wingSpread = wingSpread;
    }
}
