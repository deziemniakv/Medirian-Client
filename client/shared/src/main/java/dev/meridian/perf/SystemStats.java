package dev.meridian.perf;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.reflect.Method;

/** JVM memory and process CPU usage, sampled at a low rate. */
public final class SystemStats {

    private final Runtime runtime = Runtime.getRuntime();
    private OperatingSystemMXBean os; // created lazily: JMX initialisation is not free
    private Method processCpuLoad;
    private boolean cpuUnavailable;

    private long usedBytes;
    private long allocatedBytes;
    private long maxBytes;
    private double cpuLoad = -1;
    private long lastMemorySample;
    private long lastCpuSample;
    private volatile long lastCpuRequest;

    public void sample(long nowMs) {
        if (nowMs - lastMemorySample >= 500) {
            lastMemorySample = nowMs;
            allocatedBytes = runtime.totalMemory();
            usedBytes = allocatedBytes - runtime.freeMemory();
            maxBytes = runtime.maxMemory();
        }
        // CPU load is only sampled while something displays it
        if (nowMs - lastCpuSample >= 1000 && !cpuUnavailable && nowMs - lastCpuRequest < 5000) {
            lastCpuSample = nowMs;
            cpuLoad = readCpuLoad();
        }
    }

    /** {@code com.sun.management.OperatingSystemMXBean#getProcessCpuLoad} via reflection (HotSpot/OpenJ9). */
    private double readCpuLoad() {
        try {
            if (processCpuLoad == null) {
                os = ManagementFactory.getOperatingSystemMXBean();
                Class<?> type = Class.forName("com.sun.management.OperatingSystemMXBean");
                if (!type.isInstance(os)) {
                    cpuUnavailable = true;
                    return -1;
                }
                processCpuLoad = type.getMethod("getProcessCpuLoad");
            }
            Object value = processCpuLoad.invoke(os);
            return value instanceof Double ? (Double) value : -1;
        } catch (Throwable t) {
            cpuUnavailable = true;
            return -1;
        }
    }

    public long usedBytes() {
        return usedBytes;
    }

    public long allocatedBytes() {
        return allocatedBytes;
    }

    public long maxBytes() {
        return maxBytes;
    }

    public float memoryPercent() {
        return maxBytes <= 0 ? 0 : usedBytes * 100f / maxBytes;
    }

    /** Process CPU load 0..1 across all cores, or -1 when the JVM does not expose it. */
    public double cpuLoad() {
        lastCpuRequest = System.currentTimeMillis();
        return cpuLoad;
    }
}
