package dev.medirian.mc1_21_8.mixin;

import dev.medirian.platform.Hooks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.io.File;
import java.util.function.Consumer;

/**
 * Screenshot Tool: observes the result message of {@code Screenshot.grab} to learn the saved
 * file, and optionally replaces the vanilla chat line with a Medirian notification.
 */
@Mixin(Screenshot.class)
public abstract class ScreenshotMixin {

    @ModifyVariable(method = "grab(Ljava/io/File;Ljava/lang/String;Lcom/mojang/blaze3d/pipeline/RenderTarget;ILjava/util/function/Consumer;)V",
            at = @At("HEAD"), argsOnly = true)
    private static Consumer<Component> medirian$wrapCallback(Consumer<Component> callback) {
        return message -> {
            if (message.getContents() instanceof TranslatableContents contents
                    && "screenshot.success".equals(contents.getKey())
                    && contents.getArgs().length > 0
                    && contents.getArgs()[0] instanceof Component fileName) {
                File directory = new File(Minecraft.getInstance().gameDirectory, Screenshot.SCREENSHOT_DIR);
                Hooks.screenshot(new File(directory, fileName.getString()));
                if (Hooks.suppressScreenshotChat()) {
                    return;
                }
            }
            callback.accept(message);
        };
    }
}
