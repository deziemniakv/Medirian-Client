package dev.meridian.mc1_8_9;

import dev.meridian.input.Key;
import org.lwjgl.input.Keyboard;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/** LWJGL 2 key code ↔ portable {@link Key} mapping. */
public final class KeyCodes {

    private static final Map<Integer, Key> TO_KEY = new HashMap<Integer, Key>();
    private static final Map<Key, Integer> TO_LWJGL = new EnumMap<Key, Integer>(Key.class);

    static {
        int[] letters = {Keyboard.KEY_A, Keyboard.KEY_B, Keyboard.KEY_C, Keyboard.KEY_D, Keyboard.KEY_E, Keyboard.KEY_F,
                Keyboard.KEY_G, Keyboard.KEY_H, Keyboard.KEY_I, Keyboard.KEY_J, Keyboard.KEY_K, Keyboard.KEY_L, Keyboard.KEY_M,
                Keyboard.KEY_N, Keyboard.KEY_O, Keyboard.KEY_P, Keyboard.KEY_Q, Keyboard.KEY_R, Keyboard.KEY_S, Keyboard.KEY_T,
                Keyboard.KEY_U, Keyboard.KEY_V, Keyboard.KEY_W, Keyboard.KEY_X, Keyboard.KEY_Y, Keyboard.KEY_Z};
        for (int i = 0; i < letters.length; i++) {
            map(letters[i], Key.valueOf(String.valueOf((char) ('A' + i))));
        }
        int[] digits = {Keyboard.KEY_0, Keyboard.KEY_1, Keyboard.KEY_2, Keyboard.KEY_3, Keyboard.KEY_4, Keyboard.KEY_5,
                Keyboard.KEY_6, Keyboard.KEY_7, Keyboard.KEY_8, Keyboard.KEY_9};
        int[] numpad = {Keyboard.KEY_NUMPAD0, Keyboard.KEY_NUMPAD1, Keyboard.KEY_NUMPAD2, Keyboard.KEY_NUMPAD3, Keyboard.KEY_NUMPAD4,
                Keyboard.KEY_NUMPAD5, Keyboard.KEY_NUMPAD6, Keyboard.KEY_NUMPAD7, Keyboard.KEY_NUMPAD8, Keyboard.KEY_NUMPAD9};
        for (int i = 0; i <= 9; i++) {
            map(digits[i], Key.valueOf("NUM_" + i));
            map(numpad[i], Key.valueOf("NUMPAD_" + i));
        }
        int[] functions = {Keyboard.KEY_F1, Keyboard.KEY_F2, Keyboard.KEY_F3, Keyboard.KEY_F4, Keyboard.KEY_F5, Keyboard.KEY_F6,
                Keyboard.KEY_F7, Keyboard.KEY_F8, Keyboard.KEY_F9, Keyboard.KEY_F10, Keyboard.KEY_F11, Keyboard.KEY_F12};
        for (int i = 0; i < functions.length; i++) {
            map(functions[i], Key.valueOf("F" + (i + 1)));
        }
        map(Keyboard.KEY_SPACE, Key.SPACE);
        map(Keyboard.KEY_TAB, Key.TAB);
        map(Keyboard.KEY_CAPITAL, Key.CAPS_LOCK);
        map(Keyboard.KEY_RETURN, Key.ENTER);
        map(Keyboard.KEY_NUMPADENTER, Key.ENTER);
        map(Keyboard.KEY_BACK, Key.BACKSPACE);
        map(Keyboard.KEY_ESCAPE, Key.ESCAPE);
        map(Keyboard.KEY_LSHIFT, Key.LSHIFT);
        map(Keyboard.KEY_RSHIFT, Key.RSHIFT);
        map(Keyboard.KEY_LCONTROL, Key.LCONTROL);
        map(Keyboard.KEY_RCONTROL, Key.RCONTROL);
        map(Keyboard.KEY_LMENU, Key.LALT);
        map(Keyboard.KEY_RMENU, Key.RALT);
        map(Keyboard.KEY_UP, Key.UP);
        map(Keyboard.KEY_DOWN, Key.DOWN);
        map(Keyboard.KEY_LEFT, Key.LEFT);
        map(Keyboard.KEY_RIGHT, Key.RIGHT);
        map(Keyboard.KEY_INSERT, Key.INSERT);
        map(Keyboard.KEY_DELETE, Key.DELETE);
        map(Keyboard.KEY_HOME, Key.HOME);
        map(Keyboard.KEY_END, Key.END);
        map(Keyboard.KEY_PRIOR, Key.PAGE_UP);
        map(Keyboard.KEY_NEXT, Key.PAGE_DOWN);
        map(Keyboard.KEY_GRAVE, Key.GRAVE);
        map(Keyboard.KEY_MINUS, Key.MINUS);
        map(Keyboard.KEY_EQUALS, Key.EQUALS);
        map(Keyboard.KEY_LBRACKET, Key.LBRACKET);
        map(Keyboard.KEY_RBRACKET, Key.RBRACKET);
        map(Keyboard.KEY_BACKSLASH, Key.BACKSLASH);
        map(Keyboard.KEY_SEMICOLON, Key.SEMICOLON);
        map(Keyboard.KEY_APOSTROPHE, Key.APOSTROPHE);
        map(Keyboard.KEY_COMMA, Key.COMMA);
        map(Keyboard.KEY_PERIOD, Key.PERIOD);
        map(Keyboard.KEY_SLASH, Key.SLASH);
    }

    private KeyCodes() {
    }

    private static void map(int code, Key key) {
        TO_KEY.put(code, key);
        if (!TO_LWJGL.containsKey(key)) {
            TO_LWJGL.put(key, code);
        }
    }

    public static Key fromLwjgl(int code) {
        Key key = TO_KEY.get(code);
        return key == null ? Key.NONE : key;
    }

    /** LWJGL code of a keyboard key, or -1 for mouse keys / unmapped keys. */
    public static int toLwjgl(Key key) {
        Integer code = TO_LWJGL.get(key);
        return code == null ? -1 : code;
    }
}
