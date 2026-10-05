package dev.medirian.platform;

import dev.medirian.input.Key;

/** Physical input state. */
public interface InputView {

    /** Vanilla game controls whose bound key Medirian reads (Keystrokes, Toggle Sprint…). */
    enum GameKey { FORWARD, BACK, LEFT, RIGHT, JUMP, SNEAK, SPRINT, ATTACK, USE }

    /** Physical state of the key bound to {@code key} (ignores Medirian's forced states). */
    boolean isDown(GameKey key);

    /** Short label of the key bound to {@code key}, e.g. "W". */
    String label(GameKey key);

    boolean isKeyDown(Key key);
}
