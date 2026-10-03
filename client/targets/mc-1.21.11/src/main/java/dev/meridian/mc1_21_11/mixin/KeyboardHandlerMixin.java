package dev.meridian.mc1_21_11.mixin;

import dev.meridian.mc1_21_11.KeyCodes;
import dev.meridian.platform.Hooks;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Forwards key presses made while playing (no screen open) to Meridian keybinds. */
@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerMixin {

    @Inject(method = "keyPress", at = @At("HEAD"))
    private void meridian$key(long window, int action, KeyEvent event, CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        if (window != minecraft.getWindow().handle() || minecraft.screen != null || action == GLFW.GLFW_REPEAT) {
            return;
        }
        Hooks.keyEvent(KeyCodes.fromGlfw(event.key()), action == GLFW.GLFW_PRESS);
    }
}
