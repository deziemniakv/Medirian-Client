package dev.medirian.mc1_8_9.mixin;

import dev.medirian.mc1_8_9.KeyCodes;
import dev.medirian.platform.Hooks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import org.lwjgl.input.Keyboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Input routing and forced keys. {@code setKeyPressed} receives every in-game key and mouse
 * button event (mouse buttons as {@code button - 100}).
 */
@Mixin(KeyBinding.class)
public abstract class KeyBindingMixin {

    @Inject(method = "setKeyPressed", at = @At("HEAD"))
    private static void medirian$input(int code, boolean pressed, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.currentScreen != null) {
            return;
        }
        if (code < 0) {
            Hooks.mouseButton(code + 100, pressed);
        } else if (!Keyboard.isRepeatEvent()) {
            Hooks.keyEvent(KeyCodes.fromLwjgl(code), pressed);
        }
    }

    @Inject(method = "isPressed", at = @At("RETURN"), cancellable = true)
    private void medirian$forced(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) {
            return;
        }
        GameOptions options = MinecraftClient.getInstance().options;
        if (options == null) {
            return;
        }
        Object self = this;
        if ((self == options.sprintKey && Hooks.forceSprint()) || (self == options.sneakKey && Hooks.forceSneak())) {
            cir.setReturnValue(true);
        }
    }
}
