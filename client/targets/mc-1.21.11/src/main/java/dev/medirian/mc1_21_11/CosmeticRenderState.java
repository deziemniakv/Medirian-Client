package dev.medirian.mc1_21_11;

import dev.medirian.cosmetics.Cosmetic;

/**
 * Worn cosmetics resolved while the player's render state is extracted (on the client thread), for
 * the cosmetics layer to draw later. Implemented on {@code AvatarRenderState} by a mixin.
 */
public interface CosmeticRenderState {

    Cosmetic medirian$hat();

    Cosmetic medirian$wings();

    float medirian$wingSpread();

    void medirian$setCosmetics(Cosmetic hat, Cosmetic wings, float wingSpread);

    /** The emote pose for this frame, or null when the player plays no emote. */
    dev.medirian.cosmetics.emote.EmotePose medirian$emote();

    /** The state's own pose object, filled during extraction. */
    dev.medirian.cosmetics.emote.EmotePose medirian$emoteBuffer();

    void medirian$setEmoting(boolean emoting);
}
