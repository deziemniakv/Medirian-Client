package dev.meridian.mc26_3;

import dev.meridian.cosmetics.Cosmetic;

/**
 * Worn cosmetics resolved while the player's render state is extracted (on the client thread), for
 * the cosmetics layer to draw later. Implemented on {@code AvatarRenderState} by a mixin.
 */
public interface CosmeticRenderState {

    Cosmetic meridian$hat();

    Cosmetic meridian$wings();

    float meridian$wingSpread();

    void meridian$setCosmetics(Cosmetic hat, Cosmetic wings, float wingSpread);

    /** The emote pose for this frame, or null when the player plays no emote. */
    dev.meridian.cosmetics.emote.EmotePose meridian$emote();

    /** The state's own pose object, filled during extraction. */
    dev.meridian.cosmetics.emote.EmotePose meridian$emoteBuffer();

    void meridian$setEmoting(boolean emoting);
}
