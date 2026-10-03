package dev.meridian.mc1_8_9.mixin;

import dev.meridian.platform.Hooks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;
import java.util.List;

/**
 * Chat messages for modules (Auto GG), screenshot detection (1.8.9 saves screenshots
 * synchronously and reports them as a "screenshot.success" chat message) and the Chat module:
 * timestamps, stacking of repeated lines and the history length.
 */
@Mixin(ChatHud.class)
public abstract class ChatHudMixin {

    @Shadow @Final private List<ChatHudLine> messages;
    @Shadow @Final private List<ChatHudLine> visibleMessages;
    @Shadow private int scrolledLines;

    @Shadow public abstract void reset();

    @Shadow public abstract void scroll(int lines);

    /** The newest message added with id 0; stacking only replaces this one. */
    @Unique private ChatHudLine meridian$last;
    /** First visible line below {@link #meridian$last} (null when it was the only message). */
    @Unique private ChatHudLine meridian$lastBoundary;
    @Unique private ChatHudLine meridian$pendingBoundary;

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

    /** Decorates new messages (refreshes re-add existing lines and do not pass through here). */
    @ModifyArg(method = "addMessage(Lnet/minecraft/text/Text;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/ChatHud;addMessage(Lnet/minecraft/text/Text;IIZ)V"),
            index = 0)
    private Text meridian$decorate(Text message, int id, int ticks, boolean refresh) {
        String plain = message.asUnformattedString();
        boolean previousShown = id == 0 && meridian$last != null && !messages.isEmpty() && messages.get(0) == meridian$last;
        int count = Hooks.chatStack(plain, previousShown);
        if (count > 1) {
            meridian$removeNewest();
        }
        String timestamp = Hooks.chatTimestamp(plain);
        if (timestamp == null && count <= 1) {
            return message;
        }
        Text decorated = new LiteralText("");
        if (timestamp != null) {
            decorated.append(new LiteralText(timestamp).setStyle(new Style().setFormatting(Formatting.GRAY)));
        }
        decorated.append(message);
        if (count > 1) {
            decorated.append(new LiteralText(" (x" + count + ")").setStyle(new Style().setFormatting(Formatting.GRAY)));
        }
        return decorated;
    }

    @Inject(method = "addMessage(Lnet/minecraft/text/Text;IIZ)V", at = @At("HEAD"))
    private void meridian$beforeAdd(Text message, int id, int ticks, boolean refresh, CallbackInfo ci) {
        if (!refresh) {
            meridian$pendingBoundary = visibleMessages.isEmpty() ? null : visibleMessages.get(0);
        }
    }

    @Inject(method = "addMessage(Lnet/minecraft/text/Text;IIZ)V", at = @At("TAIL"))
    private void meridian$afterAdd(Text message, int id, int ticks, boolean refresh, CallbackInfo ci) {
        if (!refresh) {
            meridian$last = id == 0 && !messages.isEmpty() ? messages.get(0) : null;
            meridian$lastBoundary = meridian$pendingBoundary;
        }
    }

    /** Removes the newest message and its wrapped lines (all visible lines above its boundary). */
    @Unique
    private void meridian$removeNewest() {
        messages.remove(0);
        int lines = -1;
        if (meridian$lastBoundary == null) {
            lines = visibleMessages.size();
        } else {
            for (int i = 0; i < visibleMessages.size(); i++) {
                if (visibleMessages.get(i) == meridian$lastBoundary) {
                    lines = i;
                    break;
                }
            }
        }
        meridian$last = null;
        if (lines < 0) {
            // the visible lines were rebuilt (resize, settings change): rebuild them from the messages
            reset();
            return;
        }
        for (int i = 0; i < lines; i++) {
            visibleMessages.remove(0);
        }
        if (scrolledLines > 0) {
            scroll(-lines);
        }
    }

    @ModifyConstant(method = "addMessage(Lnet/minecraft/text/Text;IIZ)V", constant = @Constant(intValue = 100))
    private int meridian$history(int vanilla) {
        return Hooks.chatHistory(vanilla);
    }
}
