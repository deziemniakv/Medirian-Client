package dev.medirian.mc1_21_8.mixin;

import dev.medirian.mc1_21_8.ChatLookup;
import dev.medirian.platform.Hooks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Chat module: right-clicking a line in the open chat copies its message. */
@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void medirian$copy(double x, double y, int button, CallbackInfoReturnable<Boolean> cir) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_RIGHT || !Hooks.chatCopyEnabled()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        String message = ((ChatLookup) minecraft.gui.getChat()).medirian$messageAt(x, y);
        if (message != null) {
            minecraft.keyboardHandler.setClipboard(Hooks.chatCopied(message));
            cir.setReturnValue(true);
        }
    }
}
