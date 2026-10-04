package dev.meridian.mc1_21_11;

/** Implemented on ChatComponent by ChatComponentMixin: finds the chat message under the mouse. */
public interface ChatLookup {

    /** Plain text of the whole message whose line is at the GUI position, or null. Only while the chat is open. */
    String meridian$messageAt(double guiX, double guiY);
}
