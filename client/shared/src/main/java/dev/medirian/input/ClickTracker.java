package dev.medirian.input;

/**
 * Counts clicks inside a sliding time window (clicks per second).
 *
 * <p>Timestamps are kept in a fixed ring buffer, so recording a click and reading the CPS never
 * allocate. The buffer holds 64 clicks, which is more than any human can produce within the
 * maximum supported window of 3 s at realistic rates.
 */
public final class ClickTracker {

    private static final int CAPACITY = 64;

    private final long[] times = new long[CAPACITY];
    private int head;
    private int size;
    private long lastClickMs;

    public synchronized void click(long nowMs) {
        times[head] = nowMs;
        head = (head + 1) % CAPACITY;
        if (size < CAPACITY) {
            size++;
        }
        lastClickMs = nowMs;
    }

    /** Number of clicks within the last {@code windowMs}, normalised to clicks per second. */
    public synchronized float cps(long nowMs, long windowMs) {
        int count = 0;
        for (int i = 0; i < size; i++) {
            int index = (head - 1 - i + CAPACITY) % CAPACITY;
            if (nowMs - times[index] <= windowMs) {
                count++;
            } else {
                break;
            }
        }
        return count * 1000f / windowMs;
    }

    public synchronized long lastClickMs() {
        return lastClickMs;
    }

    public synchronized void reset() {
        head = 0;
        size = 0;
        lastClickMs = 0;
    }
}
