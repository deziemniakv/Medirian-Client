package dev.medirian.mc26_3.mixin;

import dev.medirian.mc26_3.ModernCapes;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Medirian capes. */
@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin {

    @Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
    private void medirian$cape(CallbackInfoReturnable<PlayerSkin> cir) {
        PlayerSkin skin = ModernCapes.withCape((AbstractClientPlayer) (Object) this, cir.getReturnValue());
        if (skin != null) {
            cir.setReturnValue(skin);
        }
    }
}
