package dev.medirian.mc1_8_9.mixin;

import dev.medirian.mc1_8_9.ChatLookup;
import dev.medirian.platform.Hooks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import org.lwjgl.input.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Chat module: right-clicking a line in the open chat copies its message. */
@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void medirian$copy(int mouseX, int mouseY, int button, CallbackInfo ci) {
        if (button != 1 || !Hooks.chatCopyEnabled()) {
            return;
        }
        ChatLookup chat = (ChatLookup) MinecraftClient.getInstance().inGameHud.getChatHud();
        // vanilla's chat hit test works in raw window coordinates
        String message = chat.medirian$messageAt(Mouse.getX(), Mouse.getY());
        if (message != null) {
            Screen.setClipboard(Hooks.chatCopied(message));
            ci.cancel();
        }
    }
}
