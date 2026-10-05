package dev.medirian.mc1_8_9;

/** Implemented on ChatHud by ChatHudMixin: finds the chat message under the mouse. */
public interface ChatLookup {

    /**
     * Plain text of the whole message whose line is under the mouse, or null. Takes raw window
     * coordinates (LWJGL 2, origin bottom-left) like vanilla's {@code getTextAt}. Only while the chat is open.
     */
    String medirian$messageAt(int rawMouseX, int rawMouseY);
}
