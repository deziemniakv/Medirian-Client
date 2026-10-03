package dev.meridian.event;

import dev.meridian.core.Log;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Typed, allocation-free event bus.
 *
 * <p>Listeners are stored per exact event class in copy-on-write arrays, so {@link #post} is a
 * map lookup plus an array iteration — no reflection and no allocation on the hot path (tick,
 * frame and HUD render events are posted every frame). Subscriptions happen rarely (module
 * enable/disable) and are synchronized.
 *
 * <p>A listener that throws is logged once and never crashes the game.
 */
public final class EventBus {

    private static final Listener<?>[] EMPTY = new Listener<?>[0];

    private volatile Map<Class<?>, Listener<?>[]> listeners = new HashMap<Class<?>, Listener<?>[]>();
    private final Set<Listener<?>> reportedFailures = new HashSet<Listener<?>>();

    public synchronized <E> void subscribe(Class<E> type, Listener<? super E> listener) {
        Map<Class<?>, Listener<?>[]> copy = new HashMap<Class<?>, Listener<?>[]>(listeners);
        Listener<?>[] current = copy.get(type);
        if (current == null) {
            current = EMPTY;
        }
        for (Listener<?> existing : current) {
            if (existing == listener) {
                return;
            }
        }
        Listener<?>[] next = Arrays.copyOf(current, current.length + 1);
        next[current.length] = listener;
        copy.put(type, next);
        listeners = copy;
    }

    public synchronized <E> void unsubscribe(Class<E> type, Listener<? super E> listener) {
        Listener<?>[] current = listeners.get(type);
        if (current == null) {
            return;
        }
        int index = -1;
        for (int i = 0; i < current.length; i++) {
            if (current[i] == listener) {
                index = i;
                break;
            }
        }
        if (index < 0) {
            return;
        }
        Listener<?>[] next = new Listener<?>[current.length - 1];
        System.arraycopy(current, 0, next, 0, index);
        System.arraycopy(current, index + 1, next, index, current.length - index - 1);
        Map<Class<?>, Listener<?>[]> copy = new HashMap<Class<?>, Listener<?>[]>(listeners);
        if (next.length == 0) {
            copy.remove(type);
        } else {
            copy.put(type, next);
        }
        listeners = copy;
    }

    public boolean hasListeners(Class<?> type) {
        return listeners.containsKey(type);
    }

    @SuppressWarnings("unchecked")
    public <E> E post(E event) {
        Listener<?>[] targets = listeners.get(event.getClass());
        if (targets == null) {
            return event;
        }
        for (Listener<?> target : targets) {
            try {
                ((Listener<E>) target).handle(event);
            } catch (Throwable t) {
                reportFailure(target, event, t);
            }
        }
        return event;
    }

    private void reportFailure(Listener<?> listener, Object event, Throwable t) {
        boolean first;
        synchronized (reportedFailures) {
            first = reportedFailures.add(listener);
        }
        if (first) {
            Log.error("Listener {} failed while handling {}", listener, event.getClass().getSimpleName(), t);
        }
    }
}
