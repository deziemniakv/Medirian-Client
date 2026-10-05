package dev.medirian.mc1_21_8.mixin;

import dev.medirian.platform.Hooks;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Time Changer: the client sees the overridden time of day (render only). */
@Mixin(ClientLevel.ClientLevelData.class)
public abstract class ClientLevelDataMixin {

    @Inject(method = "getDayTime", at = @At("RETURN"), cancellable = true)
    private void medirian$dayTime(CallbackInfoReturnable<Long> cir) {
        long vanilla = cir.getReturnValueJ();
        long time = Hooks.timeOfDay(vanilla);
        if (time != vanilla) {
            cir.setReturnValue(time);
        }
    }
}
