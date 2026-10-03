package dev.meridian.perf;

/**
 * Frame time history (ring buffer of the last {@link #CAPACITY} frames) with derived FPS figures.
 * Recording is allocation-free; derived values are recomputed at most 4 times per second.
 */
public final class FrameStats {

    public static final int CAPACITY = 240;

    private final float[] frameMs = new float[CAPACITY];
    private int head;
    private int size;
    private long lastFrameNanos;

    private long lastComputeNanos;
    private float averageFps;
    private float averageFrameMs;
    private float onePercentLowFps;
    private final float[] sortBuffer = new float[CAPACITY];

    /** Records the start of a frame. */
    public void frame(long nowNanos) {
        if (lastFrameNanos != 0) {
            float ms = (nowNanos - lastFrameNanos) / 1_000_000f;
            if (ms > 0 && ms < 5000) {
                frameMs[head] = ms;
                head = (head + 1) % CAPACITY;
                if (size < CAPACITY) {
                    size++;
                }
            }
        }
        lastFrameNanos = nowNanos;
        if (nowNanos - lastComputeNanos > 250_000_000L) {
            compute();
            lastComputeNanos = nowNanos;
        }
    }

    private void compute() {
        if (size == 0) {
            return;
        }
        // average over the frames of the last second (at least 1 frame)
        float total = 0;
        int counted = 0;
        for (int i = 0; i < size; i++) {
            float ms = frameMs[(head - 1 - i + CAPACITY) % CAPACITY];
            total += ms;
            counted++;
            if (total >= 1000f) {
                break;
            }
        }
        averageFrameMs = total / counted;
        averageFps = averageFrameMs > 0 ? 1000f / averageFrameMs : 0;

        // 1% low: FPS of the slowest 1% of recorded frames
        System.arraycopy(frameMs, 0, sortBuffer, 0, size);
        java.util.Arrays.sort(sortBuffer, 0, size);
        int worst = Math.max(1, size / 100);
        float worstTotal = 0;
        for (int i = size - worst; i < size; i++) {
            worstTotal += sortBuffer[i];
        }
        float worstMs = worstTotal / worst;
        onePercentLowFps = worstMs > 0 ? 1000f / worstMs : 0;
    }

    public float averageFps() {
        return averageFps;
    }

    public float averageFrameMs() {
        return averageFrameMs;
    }

    public float onePercentLowFps() {
        return onePercentLowFps;
    }

    public int size() {
        return size;
    }

    /** Frame time {@code age} frames ago (0 = most recent). */
    public float frameMs(int age) {
        if (age >= size) {
            return 0;
        }
        return frameMs[(head - 1 - age + CAPACITY) % CAPACITY];
    }
}
