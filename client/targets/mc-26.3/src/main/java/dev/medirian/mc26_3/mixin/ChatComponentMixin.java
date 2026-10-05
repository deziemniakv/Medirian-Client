package dev.medirian.mc26_3.mixin;

import dev.medirian.mc26_3.ChatLookup;
import dev.medirian.platform.Hooks;
import net.minecraft.ChatFormatting;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Chat events for modules such as Auto GG, and the Chat module: timestamps, stacking of repeated
 * lines, the history length and finding the message under the mouse (copying).
 */
@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin implements ChatLookup {

    @Shadow @Final private Minecraft minecraft;

    @Shadow @Final private List<GuiMessage> allMessages;
    @Shadow @Final private List<GuiMessage.Line> trimmedMessages;
    @Shadow private int chatScrollbarPos;

    @Shadow public abstract void scrollChat(int lines);

    @Shadow public abstract boolean isChatFocused();

    @Shadow public abstract int getLinesPerPage();

    @Shadow private double getScale() {
        throw new AssertionError();
    }

    /**
     * Same layout as {@code render(ChatGraphicsAccess, int, int, boolean)}: the pose is
     * scale(s) · translate(4, 0); line {@code row} (0 = bottom) spans
     * [base − (row + 1)·h, base − row·h) with base = ⌊(guiHeight − 40) / s⌋ and h = 9 · (spacing + 1).
     */
    @Override
    public String medirian$messageAt(double guiX, double guiY) {
        if (!isChatFocused() || trimmedMessages.isEmpty()) {
            return null;
        }
        double scale = getScale();
        int base = Mth.floor((minecraft.getWindow().getGuiScaledHeight() - 40) / (float) scale);
        int lineHeight = (int) (9 * (minecraft.options.chatLineSpacing().get() + 1.0));
        double localX = guiX / scale - 4;
        double localY = guiY / scale;
        int row = Mth.floor((base - localY) / lineHeight);
        int visible = Math.min(trimmedMessages.size() - chatScrollbarPos, getLinesPerPage());
        if (localX < -4 || localX > ChatComponent.getWidth(minecraft.options.chatWidth().get()) / scale + 8
                || row < 0 || row >= visible) {
            return null;
        }
        // lines are stored newest first; every message's newest line carries endOfEntry
        int lineIndex = row + chatScrollbarPos;
        int entry = -1;
        for (int i = 0; i <= lineIndex; i++) {
            if (trimmedMessages.get(i).endOfEntry()) {
                entry++;
            }
        }
        return entry >= 0 && entry < allMessages.size() ? allMessages.get(entry).content().getString() : null;
    }

    /** The newest message added through {@code addMessage}; stacking only replaces this one. */
    @Unique private GuiMessage medirian$last;

    @ModifyVariable(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V",
            at = @At("HEAD"), argsOnly = true)
    private Component medirian$decorate(Component message) {
        String plain = message.getString();
        Hooks.chat(plain);
        boolean previousShown = medirian$last != null && !allMessages.isEmpty() && allMessages.getFirst() == medirian$last;
        int count = Hooks.chatStack(plain, previousShown);
        if (count > 1) {
            medirian$removeNewest();
        }
        String timestamp = Hooks.chatTimestamp(plain);
        if (timestamp == null && count <= 1) {
            return message;
        }
        MutableComponent decorated = Component.empty();
        if (timestamp != null) {
            decorated.append(Component.literal(timestamp).withStyle(ChatFormatting.GRAY));
        }
        decorated.append(message);
        if (count > 1) {
            decorated.append(Component.literal(" (x" + count + ")").withStyle(ChatFormatting.GRAY));
        }
        return decorated;
    }

    @Inject(method = "addMessageToQueue", at = @At("TAIL"))
    private void medirian$track(GuiMessage message, CallbackInfo ci) {
        medirian$last = message;
    }

    /** Removes the newest message and its wrapped lines (newest line first, the entry ends at {@code endOfEntry}). */
    @Unique
    private void medirian$removeNewest() {
        allMessages.removeFirst();
        int removed = 0;
        while (!trimmedMessages.isEmpty()) {
            boolean first = removed == 0;
            if (!first && trimmedMessages.getFirst().endOfEntry()) {
                break;
            }
            trimmedMessages.removeFirst();
            removed++;
        }
        if (chatScrollbarPos > 0) {
            // keep the scrolled-up view where it was; the new line scrolls it back by its own height
            scrollChat(-removed);
        }
        medirian$last = null;
    }

    @ModifyConstant(method = {"addMessageToDisplayQueue", "addMessageToQueue"}, constant = @Constant(intValue = 100))
    private int medirian$history(int vanilla) {
        return Hooks.chatHistory(vanilla);
    }
}
