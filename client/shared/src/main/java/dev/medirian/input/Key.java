package dev.medirian.input;

import java.util.HashMap;
import java.util.Map;

/**
 * Portable key identifiers.
 *
 * <p>Minecraft 1.8.9 uses LWJGL2 key codes and modern versions use GLFW codes, so keybinds are
 * stored by {@link #name()} and each adapter maps its native codes to these constants. This makes
 * configuration profiles portable between versions.
 */
public enum Key {
    NONE("None"),
    A("A"), B("B"), C("C"), D("D"), E("E"), F("F"), G("G"), H("H"), I("I"), J("J"), K("K"), L("L"), M("M"),
    N("N"), O("O"), P("P"), Q("Q"), R("R"), S("S"), T("T"), U("U"), V("V"), W("W"), X("X"), Y("Y"), Z("Z"),
    NUM_0("0"), NUM_1("1"), NUM_2("2"), NUM_3("3"), NUM_4("4"), NUM_5("5"), NUM_6("6"), NUM_7("7"), NUM_8("8"), NUM_9("9"),
    F1("F1"), F2("F2"), F3("F3"), F4("F4"), F5("F5"), F6("F6"), F7("F7"), F8("F8"), F9("F9"), F10("F10"), F11("F11"), F12("F12"),
    SPACE("Space"), TAB("Tab"), CAPS_LOCK("Caps"), ENTER("Enter"), BACKSPACE("Backspace"), ESCAPE("Esc"),
    LSHIFT("LShift"), RSHIFT("RShift"), LCONTROL("LCtrl"), RCONTROL("RCtrl"), LALT("LAlt"), RALT("RAlt"),
    UP("Up"), DOWN("Down"), LEFT("Left"), RIGHT("Right"),
    INSERT("Insert"), DELETE("Delete"), HOME("Home"), END("End"), PAGE_UP("PgUp"), PAGE_DOWN("PgDn"),
    GRAVE("`"), MINUS("-"), EQUALS("="), LBRACKET("["), RBRACKET("]"), BACKSLASH("\\"), SEMICOLON(";"),
    APOSTROPHE("'"), COMMA(","), PERIOD("."), SLASH("/"),
    NUMPAD_0("Num0"), NUMPAD_1("Num1"), NUMPAD_2("Num2"), NUMPAD_3("Num3"), NUMPAD_4("Num4"),
    NUMPAD_5("Num5"), NUMPAD_6("Num6"), NUMPAD_7("Num7"), NUMPAD_8("Num8"), NUMPAD_9("Num9"),
    MOUSE_LEFT("LMB"), MOUSE_RIGHT("RMB"), MOUSE_MIDDLE("MMB"), MOUSE_4("Mouse 4"), MOUSE_5("Mouse 5");

    private static final Map<String, Key> BY_NAME = new HashMap<String, Key>();

    static {
        for (Key key : values()) {
            BY_NAME.put(key.name(), key);
        }
    }

    private final String label;

    Key(String label) {
        this.label = label;
    }

    /** Short human readable label, e.g. {@code "LShift"}. */
    public String label() {
        return label;
    }

    public boolean isMouse() {
        return this == MOUSE_LEFT || this == MOUSE_RIGHT || this == MOUSE_MIDDLE || this == MOUSE_4 || this == MOUSE_5;
    }

    /** Mouse button index (0 = left) for mouse keys, -1 otherwise. */
    public int mouseButton() {
        switch (this) {
            case MOUSE_LEFT: return 0;
            case MOUSE_RIGHT: return 1;
            case MOUSE_MIDDLE: return 2;
            case MOUSE_4: return 3;
            case MOUSE_5: return 4;
            default: return -1;
        }
    }

    public static Key fromMouseButton(int button) {
        switch (button) {
            case 0: return MOUSE_LEFT;
            case 1: return MOUSE_RIGHT;
            case 2: return MOUSE_MIDDLE;
            case 3: return MOUSE_4;
            case 4: return MOUSE_5;
            default: return NONE;
        }
    }

    public static Key byName(String name) {
        if (name == null) {
            return NONE;
        }
        Key key = BY_NAME.get(name.toUpperCase());
        return key == null ? NONE : key;
    }
}
