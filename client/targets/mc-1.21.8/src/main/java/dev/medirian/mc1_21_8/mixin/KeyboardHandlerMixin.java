package dev.medirian.mc1_21_8.mixin;

import dev.medirian.mc1_21_8.KeyCodes;
import dev.medirian.platform.Hooks;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Forwards key presses made while playing (no screen open) to Medirian keybinds. */
@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerMixin {

    @Inject(method = "keyPress", at = @At("HEAD"))
    private void medirian$key(long window, int key, int scanCode, int action, int modifiers, CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        if (window != minecraft.getWindow().getWindow() || minecraft.screen != null || action == GLFW.GLFW_REPEAT) {
            return;
        }
        Hooks.keyEvent(KeyCodes.fromGlfw(key), action == GLFW.GLFW_PRESS);
    }
}
