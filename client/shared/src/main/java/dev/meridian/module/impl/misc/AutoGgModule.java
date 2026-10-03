package dev.meridian.module.impl.misc;

import dev.meridian.core.Meridian;
import dev.meridian.event.Events;
import dev.meridian.module.Category;
import dev.meridian.module.Module;
import dev.meridian.platform.Capability;
import dev.meridian.setting.NumberSetting;
import dev.meridian.setting.TextSetting;

/**
 * Sends a message (default "gg") when a game ends. End-of-game detection matches chat lines
 * against configurable trigger phrases ({@code |}-separated); defaults cover common minigame
 * servers. A cooldown prevents duplicates.
 */
public final class AutoGgModule extends Module {

    private static final long COOLDOWN_MS = 10_000;

    private final TextSetting message;
    private final NumberSetting delay;
    private final TextSetting triggers;

    private long lastSentMs;
    private long scheduledAtMs;

    public AutoGgModule() {
        super("autogg", "Auto GG", Category.MISC, "Says gg automatically when a game ends.");
        requires(Capability.CHAT);
        message = add(new TextSetting("message", "Message", "gg", 64));
        delay = add(new NumberSetting("delay", "Delay", 500, 0, 5000, 100).unit(" ms"));
        triggers = add(new TextSetting("triggers", "Trigger phrases",
                "1st Killer -|Winner:|WINNER!|won the game|Reward Summary|Victory!|1st Place", 400)
                .description("Chat phrases that mark the end of a game, separated by |"));
        on(Events.ChatReceived.class, e -> onChat(e.plainText));
        on(Events.Tick.class, e -> sendIfDue());
    }

    private void onChat(String text) {
        long now = System.currentTimeMillis();
        if (scheduledAtMs != 0 || now - lastSentMs < COOLDOWN_MS || Meridian.get().game().isSingleplayer()) {
            return;
        }
        for (String trigger : triggers.get().split("\\|")) {
            String phrase = trigger.trim();
            if (!phrase.isEmpty() && text.contains(phrase)) {
                scheduledAtMs = now + delay.intValue();
                return;
            }
        }
    }

    private void sendIfDue() {
        if (scheduledAtMs != 0 && System.currentTimeMillis() >= scheduledAtMs) {
            scheduledAtMs = 0;
            lastSentMs = System.currentTimeMillis();
            String text = message.get().trim();
            if (!text.isEmpty()) {
                Meridian.get().platform().actions().sendChat(text);
            }
        }
    }
}
