package dev.medirian.mc26_3.mixin;

import dev.medirian.platform.Hooks;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Toggle Sprint / Toggle Sneak: report the sprint/sneak key as held while toggled. */
@Mixin(KeyMapping.class)
public abstract class KeyMappingMixin {

    @Inject(method = "isDown", at = @At("RETURN"), cancellable = true)
    private void medirian$forced(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) {
            return;
        }
        Options options = Minecraft.getInstance().options;
        if (options == null) {
            return;
        }
        Object self = this;
        if ((self == options.keySprint && Hooks.forceSprint()) || (self == options.keyShift && Hooks.forceSneak())) {
            cir.setReturnValue(true);
        }
    }
}
