package dev.meridian.mc1_8_9.mixin;

import dev.meridian.platform.Hooks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;

/**
 * Chat messages for modules (Auto GG) and screenshot detection: 1.8.9 saves screenshots
 * synchronously and reports them as a "screenshot.success" chat message.
 */
@Mixin(ChatHud.class)
public abstract class ChatHudMixin {

    @Inject(method = "addMessage(Lnet/minecraft/text/Text;)V", at = @At("HEAD"), cancellable = true)
    private void meridian$message(Text message, CallbackInfo ci) {
        if (message instanceof TranslatableText) {
            TranslatableText translatable = (TranslatableText) message;
            Object[] args = translatable.getArgs();
            if ("screenshot.success".equals(translatable.getKey()) && args.length > 0 && args[0] instanceof Text) {
                File directory = new File(MinecraftClient.getInstance().runDirectory, "screenshots");
                Hooks.screenshot(new File(directory, ((Text) args[0]).asUnformattedString()));
                if (Hooks.suppressScreenshotChat()) {
                    ci.cancel();
                }
                return;
            }
        }
        Hooks.chat(message.asUnformattedString());
    }
}
