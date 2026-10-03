package dev.meridian.mc1_21_11;

import dev.meridian.input.Key;
import org.lwjgl.glfw.GLFW;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/** GLFW key code ↔ portable {@link Key} mapping. */
public final class KeyCodes {

    private static final Map<Integer, Key> TO_KEY = new HashMap<>();
    private static final Map<Key, Integer> TO_GLFW = new EnumMap<>(Key.class);

    static {
        for (int i = 0; i < 26; i++) {
            map(GLFW.GLFW_KEY_A + i, Key.valueOf(String.valueOf((char) ('A' + i))));
        }
        for (int i = 0; i <= 9; i++) {
            map(GLFW.GLFW_KEY_0 + i, Key.valueOf("NUM_" + i));
            map(GLFW.GLFW_KEY_KP_0 + i, Key.valueOf("NUMPAD_" + i));
        }
        for (int i = 0; i < 12; i++) {
            map(GLFW.GLFW_KEY_F1 + i, Key.valueOf("F" + (i + 1)));
        }
        map(GLFW.GLFW_KEY_SPACE, Key.SPACE);
        map(GLFW.GLFW_KEY_TAB, Key.TAB);
        map(GLFW.GLFW_KEY_CAPS_LOCK, Key.CAPS_LOCK);
        map(GLFW.GLFW_KEY_ENTER, Key.ENTER);
        map(GLFW.GLFW_KEY_KP_ENTER, Key.ENTER);
        map(GLFW.GLFW_KEY_BACKSPACE, Key.BACKSPACE);
        map(GLFW.GLFW_KEY_ESCAPE, Key.ESCAPE);
        map(GLFW.GLFW_KEY_LEFT_SHIFT, Key.LSHIFT);
        map(GLFW.GLFW_KEY_RIGHT_SHIFT, Key.RSHIFT);
        map(GLFW.GLFW_KEY_LEFT_CONTROL, Key.LCONTROL);
        map(GLFW.GLFW_KEY_RIGHT_CONTROL, Key.RCONTROL);
        map(GLFW.GLFW_KEY_LEFT_ALT, Key.LALT);
        map(GLFW.GLFW_KEY_RIGHT_ALT, Key.RALT);
        map(GLFW.GLFW_KEY_UP, Key.UP);
        map(GLFW.GLFW_KEY_DOWN, Key.DOWN);
        map(GLFW.GLFW_KEY_LEFT, Key.LEFT);
        map(GLFW.GLFW_KEY_RIGHT, Key.RIGHT);
        map(GLFW.GLFW_KEY_INSERT, Key.INSERT);
        map(GLFW.GLFW_KEY_DELETE, Key.DELETE);
        map(GLFW.GLFW_KEY_HOME, Key.HOME);
        map(GLFW.GLFW_KEY_END, Key.END);
        map(GLFW.GLFW_KEY_PAGE_UP, Key.PAGE_UP);
        map(GLFW.GLFW_KEY_PAGE_DOWN, Key.PAGE_DOWN);
        map(GLFW.GLFW_KEY_GRAVE_ACCENT, Key.GRAVE);
        map(GLFW.GLFW_KEY_MINUS, Key.MINUS);
        map(GLFW.GLFW_KEY_EQUAL, Key.EQUALS);
        map(GLFW.GLFW_KEY_LEFT_BRACKET, Key.LBRACKET);
        map(GLFW.GLFW_KEY_RIGHT_BRACKET, Key.RBRACKET);
        map(GLFW.GLFW_KEY_BACKSLASH, Key.BACKSLASH);
        map(GLFW.GLFW_KEY_SEMICOLON, Key.SEMICOLON);
        map(GLFW.GLFW_KEY_APOSTROPHE, Key.APOSTROPHE);
        map(GLFW.GLFW_KEY_COMMA, Key.COMMA);
        map(GLFW.GLFW_KEY_PERIOD, Key.PERIOD);
        map(GLFW.GLFW_KEY_SLASH, Key.SLASH);
    }

    private KeyCodes() {
    }

    private static void map(int glfw, Key key) {
        TO_KEY.put(glfw, key);
        TO_GLFW.putIfAbsent(key, glfw);
    }

    public static Key fromGlfw(int code) {
        Key key = TO_KEY.get(code);
        return key == null ? Key.NONE : key;
    }

    /** GLFW code of a keyboard key, or -1 for mouse keys / unmapped keys. */
    public static int toGlfw(Key key) {
        Integer code = TO_GLFW.get(key);
        return code == null ? -1 : code;
    }
}
