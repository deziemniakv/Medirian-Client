package dev.meridian.module.impl.misc;

import dev.meridian.module.Category;
import dev.meridian.module.Module;
import dev.meridian.platform.Capability;
import dev.meridian.setting.BooleanSetting;
import dev.meridian.setting.ModeSetting;
import dev.meridian.setting.NumberSetting;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Chat quality-of-life: timestamps, stacking of repeated messages ("message (x3)") and a longer
 * history than vanilla's 100 messages.
 *
 * <p>Adapters decorate each incoming line with {@link #timestamp()} and ask {@link #stack} whether
 * it repeats the line shown right above it; if so they remove that line and show the new one with
 * a counter, so spam takes a single line.
 */
public final class ChatModule extends Module {

    public enum TimeFormat implements ModeSetting.Labeled {
        H24("HH:mm", "14:05"),
        H24_SECONDS("HH:mm:ss", "14:05:09"),
        H12("h:mm a", "2:05 PM");

        final String pattern;
        private final String example;

        TimeFormat(String pattern, String example) {
            this.pattern = pattern;
            this.example = example;
        }

        @Override
        public String label() {
            return example;
        }
    }

    public static final int VANILLA_HISTORY = 100;

    private final BooleanSetting timestamps;
    private final ModeSetting<TimeFormat> timeFormat;
    private final BooleanSetting stack;
    private final NumberSetting history;
    private final BooleanSetting copy;

    private SimpleDateFormat format;
    private TimeFormat formatFor;
    private String lastMessage;
    private int count;

    public ChatModule() {
        super("chat", "Chat", Category.MISC, "Timestamps, stacked repeated messages and a longer chat history.");
        requires(Capability.CHAT_UTILITIES);
        timestamps = add(new BooleanSetting("timestamps", "Timestamps", true));
        timeFormat = add(new ModeSetting<TimeFormat>("timeFormat", "Time format", TimeFormat.H24).visibleWhen(timestamps::on));
        stack = add(new BooleanSetting("stack", "Stack repeated messages", true)
                .description("A message identical to the previous one replaces it with a counter: (x2), (x3)…"));
        history = add(new NumberSetting("history", "History length", 500, VANILLA_HISTORY, 1000, 50)
                .description("Messages kept in the chat history (vanilla: 100)."));
        copy = add(new BooleanSetting("copy", "Right-click copies a message", true)
                .description("In the open chat, right-click a line to copy its whole message."));
    }

    @Override
    protected void onDisable() {
        lastMessage = null;
        count = 0;
    }

    /** "[14:05] " for a new line, or null when timestamps are off. Blank spacer lines get none. */
    public String timestamp(String plainText) {
        if (!isEnabled() || !timestamps.on() || plainText.trim().isEmpty()) {
            return null;
        }
        if (formatFor != timeFormat.get()) {
            formatFor = timeFormat.get();
            format = new SimpleDateFormat(formatFor.pattern, Locale.ENGLISH);
        }
        return "[" + format.format(new Date()) + "] ";
    }

    /**
     * Registers an incoming line and returns how many times in a row it has now arrived: 1 for a
     * new line, n &gt; 1 when the adapter should replace the line above it with "(xn)".
     *
     * @param previousShown whether the line Meridian saw last is still the newest one in the chat
     *                      (it may have been cleared, or a line Meridian does not stack came in between)
     */
    public int stack(String plainText, boolean previousShown) {
        if (!isEnabled() || !stack.on() || plainText.trim().isEmpty()) {
            lastMessage = null;
            count = 0;
            return 1;
        }
        if (previousShown && count > 0 && plainText.equals(lastMessage)) {
            count++;
        } else {
            lastMessage = plainText;
            count = 1;
        }
        return count;
    }

    /** Right-clicking a chat line copies its message. */
    public boolean copyEnabled() {
        return isEnabled() && copy.on();
    }

    /** The message text without Meridian's own decorations (timestamp prefix, "(xN)" counter). */
    public static String stripDecorations(String text) {
        return text.replaceFirst("^\\[\\d{1,2}:\\d{2}(:\\d{2})?( [AP]M)?] ", "").replaceFirst(" \\(x\\d+\\)$", "");
    }

    /** Messages (and, in modern versions, wrapped lines) kept in the chat. */
    public int history(int vanilla) {
        return isEnabled() ? Math.max(vanilla, history.intValue()) : vanilla;
    }
}
