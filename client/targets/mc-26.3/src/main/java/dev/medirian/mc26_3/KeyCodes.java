package dev.medirian.mc26_3;

import com.mojang.blaze3d.platform.InputConstants;
import dev.medirian.input.Key;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Minecraft key code ↔ portable {@link Key} mapping. Since 26.x the game uses SDL3, so the codes
 * are SDL scancodes behind {@link InputConstants}'s constants (no longer GLFW key codes, and not
 * contiguous for digits) — every key is therefore mapped by name.
 */
public final class KeyCodes {

    private static final Map<Integer, Key> TO_KEY = new HashMap<>();
    private static final Map<Key, Integer> TO_CODE = new EnumMap<>(Key.class);

    static {
        int[] letters = {
            InputConstants.KEY_A, InputConstants.KEY_B, InputConstants.KEY_C, InputConstants.KEY_D, InputConstants.KEY_E,
            InputConstants.KEY_F, InputConstants.KEY_G, InputConstants.KEY_H, InputConstants.KEY_I, InputConstants.KEY_J,
            InputConstants.KEY_K, InputConstants.KEY_L, InputConstants.KEY_M, InputConstants.KEY_N, InputConstants.KEY_O,
            InputConstants.KEY_P, InputConstants.KEY_Q, InputConstants.KEY_R, InputConstants.KEY_S, InputConstants.KEY_T,
            InputConstants.KEY_U, InputConstants.KEY_V, InputConstants.KEY_W, InputConstants.KEY_X, InputConstants.KEY_Y,
            InputConstants.KEY_Z
        };
        for (int i = 0; i < letters.length; i++) {
            map(letters[i], Key.valueOf(String.valueOf((char) ('A' + i))));
        }
        int[] digits = {
            InputConstants.KEY_0, InputConstants.KEY_1, InputConstants.KEY_2, InputConstants.KEY_3, InputConstants.KEY_4,
            InputConstants.KEY_5, InputConstants.KEY_6, InputConstants.KEY_7, InputConstants.KEY_8, InputConstants.KEY_9
        };
        int[] numpad = {
            InputConstants.KEY_NUMPAD0, InputConstants.KEY_NUMPAD1, InputConstants.KEY_NUMPAD2, InputConstants.KEY_NUMPAD3,
            InputConstants.KEY_NUMPAD4, InputConstants.KEY_NUMPAD5, InputConstants.KEY_NUMPAD6, InputConstants.KEY_NUMPAD7,
            InputConstants.KEY_NUMPAD8, InputConstants.KEY_NUMPAD9
        };
        for (int i = 0; i <= 9; i++) {
            map(digits[i], Key.valueOf("NUM_" + i));
            map(numpad[i], Key.valueOf("NUMPAD_" + i));
        }
        int[] functions = {
            InputConstants.KEY_F1, InputConstants.KEY_F2, InputConstants.KEY_F3, InputConstants.KEY_F4, InputConstants.KEY_F5,
            InputConstants.KEY_F6, InputConstants.KEY_F7, InputConstants.KEY_F8, InputConstants.KEY_F9, InputConstants.KEY_F10,
            InputConstants.KEY_F11, InputConstants.KEY_F12
        };
        for (int i = 0; i < functions.length; i++) {
            map(functions[i], Key.valueOf("F" + (i + 1)));
        }
        map(InputConstants.KEY_SPACE, Key.SPACE);
        map(InputConstants.KEY_TAB, Key.TAB);
        map(InputConstants.KEY_CAPSLOCK, Key.CAPS_LOCK);
        map(InputConstants.KEY_RETURN, Key.ENTER);
        map(InputConstants.KEY_NUMPADENTER, Key.ENTER);
        map(InputConstants.KEY_BACKSPACE, Key.BACKSPACE);
        map(InputConstants.KEY_ESCAPE, Key.ESCAPE);
        map(InputConstants.KEY_LSHIFT, Key.LSHIFT);
        map(InputConstants.KEY_RSHIFT, Key.RSHIFT);
        map(InputConstants.KEY_LCONTROL, Key.LCONTROL);
        map(InputConstants.KEY_RCONTROL, Key.RCONTROL);
        map(InputConstants.KEY_LALT, Key.LALT);
        map(InputConstants.KEY_RALT, Key.RALT);
        map(InputConstants.KEY_UP, Key.UP);
        map(InputConstants.KEY_DOWN, Key.DOWN);
        map(InputConstants.KEY_LEFT, Key.LEFT);
        map(InputConstants.KEY_RIGHT, Key.RIGHT);
        map(InputConstants.KEY_INSERT, Key.INSERT);
        map(InputConstants.KEY_DELETE, Key.DELETE);
        map(InputConstants.KEY_HOME, Key.HOME);
        map(InputConstants.KEY_END, Key.END);
        map(InputConstants.KEY_PAGEUP, Key.PAGE_UP);
        map(InputConstants.KEY_PAGEDOWN, Key.PAGE_DOWN);
        map(InputConstants.KEY_GRAVE, Key.GRAVE);
        map(InputConstants.KEY_MINUS, Key.MINUS);
        map(InputConstants.KEY_EQUALS, Key.EQUALS);
        map(InputConstants.KEY_LBRACKET, Key.LBRACKET);
        map(InputConstants.KEY_RBRACKET, Key.RBRACKET);
        map(InputConstants.KEY_BACKSLASH, Key.BACKSLASH);
        map(InputConstants.KEY_SEMICOLON, Key.SEMICOLON);
        map(InputConstants.KEY_APOSTROPHE, Key.APOSTROPHE);
        map(InputConstants.KEY_COMMA, Key.COMMA);
        map(InputConstants.KEY_PERIOD, Key.PERIOD);
        map(InputConstants.KEY_SLASH, Key.SLASH);
    }

    private KeyCodes() {
    }

    private static void map(int code, Key key) {
        TO_KEY.put(code, key);
        TO_CODE.putIfAbsent(key, code);
    }

    public static Key fromCode(int code) {
        Key key = TO_KEY.get(code);
        return key == null ? Key.NONE : key;
    }

    /** Minecraft code of a keyboard key, or -1 for mouse keys / unmapped keys. */
    public static int toCode(Key key) {
        Integer code = TO_CODE.get(key);
        return code == null ? -1 : code;
    }
}
