package dev.meridian.module;

import dev.meridian.module.impl.misc.ChatModule;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatModuleTest {

    private static ChatModule enabled() {
        ChatModule chat = new ChatModule();
        chat.setEnabled(true);
        return chat;
    }

    @Test
    void repeatedMessagesCount() {
        ChatModule chat = enabled();
        assertEquals(1, chat.stack("hello", false));
        assertEquals(2, chat.stack("hello", true));
        assertEquals(3, chat.stack("hello", true));
        assertEquals(1, chat.stack("other", true));
        assertEquals(1, chat.stack("hello", true));
    }

    @Test
    void stackingNeedsThePreviousLineOnScreen() {
        ChatModule chat = enabled();
        chat.stack("hello", false);
        // chat cleared, or an unstackable line came in between
        assertEquals(1, chat.stack("hello", false));
        assertEquals(2, chat.stack("hello", true));
    }

    @Test
    void blankLinesAndDisabledModuleNeverStack() {
        ChatModule chat = enabled();
        assertEquals(1, chat.stack("", true));
        assertEquals(1, chat.stack("", true));
        assertNull(chat.timestamp("  "));

        chat.stack("hello", true);
        chat.setEnabled(false);
        assertEquals(1, chat.stack("hello", true));
        assertNull(chat.timestamp("hello"));
        assertEquals(ChatModule.VANILLA_HISTORY, chat.history(ChatModule.VANILLA_HISTORY));
    }

    @Test
    void timestampAndHistory() {
        ChatModule chat = enabled();
        String stamp = chat.timestamp("hello");
        assertNotNull(stamp);
        assertTrue(stamp.matches("\\[\\d{2}:\\d{2}] "), stamp);
        assertEquals(500, chat.history(ChatModule.VANILLA_HISTORY));
    }
}
