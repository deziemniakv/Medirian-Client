package dev.meridian.mc26_3.mixin;

import dev.meridian.mc26_3.KeyCodes;
import dev.meridian.platform.Hooks;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import com.mojang.blaze3d.platform.InputConstants;
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
        if (window != minecraft.getWindow().handle() || minecraft.gui.screen() != null || action == InputConstants.REPEAT) {
            return;
        }
        Hooks.keyEvent(KeyCodes.fromCode(event.key()), action == InputConstants.PRESS);
    }
}
