package dev.meridian.mc26_3.mixin;

import com.mojang.blaze3d.platform.FramerateLimitTracker;
import dev.meridian.platform.Hooks;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Dynamic FPS: lower frame cap while the window is unfocused or minimised. */
@Mixin(FramerateLimitTracker.class)
public abstract class FramerateLimitTrackerMixin {

    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "getFramerateLimit", at = @At("RETURN"), cancellable = true)
    private void meridian$limit(CallbackInfoReturnable<Integer> cir) {
        int vanilla = cir.getReturnValueI();
        int limit = Hooks.framerateLimit(vanilla, minecraft.isWindowActive(), minecraft.getWindow().isIconified());
        if (limit != vanilla) {
            cir.setReturnValue(limit);
        }
    }
}
