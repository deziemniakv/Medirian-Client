package dev.meridian.mc1_8_9.mixin;

import dev.meridian.platform.Hooks;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Time Changer and Weather Changer for client worlds (render only). */
@Mixin(World.class)
public abstract class WorldMixin {

    @Inject(method = "getTimeOfDay", at = @At("RETURN"), cancellable = true)
    private void meridian$time(CallbackInfoReturnable<Long> cir) {
        if ((Object) this instanceof ClientWorld) {
            long vanilla = cir.getReturnValueJ();
            long time = Hooks.timeOfDay(vanilla);
            if (time != vanilla) {
                cir.setReturnValue(time);
            }
        }
    }

    @Inject(method = "getRainGradient", at = @At("RETURN"), cancellable = true)
    private void meridian$rain(float delta, CallbackInfoReturnable<Float> cir) {
        if ((Object) this instanceof ClientWorld) {
            cir.setReturnValue(Hooks.rainLevel(cir.getReturnValueF()));
        }
    }

    @Inject(method = "getThunderGradient", at = @At("RETURN"), cancellable = true)
    private void meridian$thunder(float delta, CallbackInfoReturnable<Float> cir) {
        if ((Object) this instanceof ClientWorld) {
            cir.setReturnValue(Hooks.thunderLevel(cir.getReturnValueF()));
        }
    }
}
