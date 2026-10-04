package dev.meridian.mc26_3.mixin;

import dev.meridian.mc26_3.ChatLookup;
import dev.meridian.platform.Hooks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.MouseButtonEvent;
import com.mojang.blaze3d.platform.InputConstants;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Chat module: right-clicking a line in the open chat copies its message. */
@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void meridian$copy(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (event.button() != InputConstants.MOUSE_BUTTON_RIGHT || !Hooks.chatCopyEnabled()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        String message = ((ChatLookup) minecraft.gui.hud.getChat()).meridian$messageAt(event.x(), event.y());
        if (message != null) {
            minecraft.keyboardHandler.setClipboard(Hooks.chatCopied(message));
            cir.setReturnValue(true);
        }
    }
}
