package dev.medirian.mc26_3.mixin;

import dev.medirian.platform.Hooks;
import net.minecraft.client.ClientClockManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Time Changer: since 26.x the time of day is read from the world clocks. */
@Mixin(ClientClockManager.ClientClockInstance.class)
public abstract class ClientLevelDataMixin {

    @Inject(method = "totalTicks", at = @At("RETURN"), cancellable = true)
    private void medirian$dayTime(CallbackInfoReturnable<Long> cir) {
        long vanilla = cir.getReturnValueJ();
        long time = Hooks.timeOfDay(vanilla);
        if (time != vanilla) {
            cir.setReturnValue(time);
        }
    }
}
