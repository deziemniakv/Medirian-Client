package dev.meridian.event;

/** Receives events of type {@code E}. */
public interface Listener<E> {
    void handle(E event);
}
