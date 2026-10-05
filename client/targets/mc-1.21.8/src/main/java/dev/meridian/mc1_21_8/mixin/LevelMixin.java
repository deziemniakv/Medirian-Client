package dev.meridian.mc1_21_8.mixin;

import dev.meridian.platform.Hooks;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Weather Changer: client levels report the overridden rain/thunder level (render only). */
@Mixin(Level.class)
public abstract class LevelMixin {

    @Inject(method = "getRainLevel", at = @At("RETURN"), cancellable = true)
    private void meridian$rain(float partialTick, CallbackInfoReturnable<Float> cir) {
        if ((Object) this instanceof ClientLevel) {
            cir.setReturnValue(Hooks.rainLevel(cir.getReturnValueF()));
        }
    }

    @Inject(method = "getThunderLevel", at = @At("RETURN"), cancellable = true)
    private void meridian$thunder(float partialTick, CallbackInfoReturnable<Float> cir) {
        if ((Object) this instanceof ClientLevel) {
            cir.setReturnValue(Hooks.thunderLevel(cir.getReturnValueF()));
        }
    }
}
