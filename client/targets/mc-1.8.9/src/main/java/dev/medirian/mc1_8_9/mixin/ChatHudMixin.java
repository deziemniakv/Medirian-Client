package dev.medirian.mc1_8_9.mixin;

import dev.medirian.mc1_8_9.ChatLookup;
import dev.medirian.platform.Hooks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.client.util.Window;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;
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
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Chat messages for modules (Auto GG), screenshot detection (1.8.9 saves screenshots
 * synchronously and reports them as a "screenshot.success" chat message) and the Chat module:
 * timestamps, stacking of repeated lines, the history length and finding the message under the
 * mouse (copying).
 */
@Mixin(ChatHud.class)
public abstract class ChatHudMixin implements ChatLookup {

    @Shadow @Final private MinecraftClient client;

    @Shadow @Final private List<ChatHudLine> messages;
    @Shadow @Final private List<ChatHudLine> visibleMessages;
    @Shadow private int scrolledLines;

    @Shadow public abstract void reset();

    @Shadow public abstract void scroll(int lines);

    @Shadow public abstract boolean isChatFocused();

    @Shadow public abstract float getChatScale();

    @Shadow public abstract int getWidth();

    @Shadow public abstract int getVisibleLineCount();

    /** The message every visible (wrapped) line belongs to; lines compare by identity. */
    @Unique private final Map<ChatHudLine, Text> medirian$lineMessages = new WeakHashMap<ChatHudLine, Text>();

    /** The newest message added with id 0; stacking only replaces this one. */
    @Unique private ChatHudLine medirian$last;
    /** First visible line below {@link #medirian$last} (null when it was the only message). */
    @Unique private ChatHudLine medirian$lastBoundary;
    @Unique private ChatHudLine medirian$pendingBoundary;

    @Inject(method = "addMessage(Lnet/minecraft/text/Text;)V", at = @At("HEAD"), cancellable = true)
    private void medirian$message(Text message, CallbackInfo ci) {
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
    private Text medirian$decorate(Text message, int id, int ticks, boolean refresh) {
        String plain = message.asUnformattedString();
        boolean previousShown = id == 0 && medirian$last != null && !messages.isEmpty() && messages.get(0) == medirian$last;
        int count = Hooks.chatStack(plain, previousShown);
        if (count > 1) {
            medirian$removeNewest();
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
    private void medirian$beforeAdd(Text message, int id, int ticks, boolean refresh, CallbackInfo ci) {
        if (!refresh) {
            medirian$pendingBoundary = visibleMessages.isEmpty() ? null : visibleMessages.get(0);
        }
    }

    @Inject(method = "addMessage(Lnet/minecraft/text/Text;IIZ)V", at = @At("TAIL"))
    private void medirian$afterAdd(Text message, int id, int ticks, boolean refresh, CallbackInfo ci) {
        // the lines just added are the newest ones not mapped yet (also when the chat is re-wrapped)
        for (int i = 0; i < visibleMessages.size() && !medirian$lineMessages.containsKey(visibleMessages.get(i)); i++) {
            medirian$lineMessages.put(visibleMessages.get(i), message);
        }
        if (!refresh) {
            medirian$last = id == 0 && !messages.isEmpty() ? messages.get(0) : null;
            medirian$lastBoundary = medirian$pendingBoundary;
        }
    }

    /** Same hit test as vanilla's getTextAt, but returns the whole message of the line. */
    @Override
    public String medirian$messageAt(int rawMouseX, int rawMouseY) {
        if (!isChatFocused()) {
            return null;
        }
        int scaleFactor = new Window(client).getScaleFactor();
        float chatScale = getChatScale();
        int x = MathHelper.floor((rawMouseX / scaleFactor - 3) / chatScale);
        int y = MathHelper.floor((rawMouseY / scaleFactor - 27) / chatScale);
        int fontHeight = client.textRenderer.fontHeight;
        int lines = Math.min(getVisibleLineCount(), visibleMessages.size());
        if (x < 0 || y < 0 || x > MathHelper.floor(getWidth() / chatScale) || y >= fontHeight * lines + lines) {
            return null;
        }
        int index = y / fontHeight + scrolledLines;
        if (index < 0 || index >= visibleMessages.size()) {
            return null;
        }
        Text message = medirian$lineMessages.get(visibleMessages.get(index));
        return message == null ? null : message.asUnformattedString();
    }

    /** Removes the newest message and its wrapped lines (all visible lines above its boundary). */
    @Unique
    private void medirian$removeNewest() {
        messages.remove(0);
        int lines = -1;
        if (medirian$lastBoundary == null) {
            lines = visibleMessages.size();
        } else {
            for (int i = 0; i < visibleMessages.size(); i++) {
                if (visibleMessages.get(i) == medirian$lastBoundary) {
                    lines = i;
                    break;
                }
            }
        }
        medirian$last = null;
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
    private int medirian$history(int vanilla) {
        return Hooks.chatHistory(vanilla);
    }
}
