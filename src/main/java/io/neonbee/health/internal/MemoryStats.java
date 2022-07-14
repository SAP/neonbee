package io.neonbee.health.internal;

public class MemoryStats {
    private final Runtime runtime = Runtime.getRuntime();

    /**
     * Returns the maximum amount of memory that the JVM will attempt to use.
     *
     * @return the maximum memory in bytes
     * @see Runtime#maxMemory()
     */
    public long getMaxHeap() {
        return runtime.maxMemory();
    }

    /**
     * Returns the amount of memory that is committed for.
     *
     * @return the committed memory in bytes
     * @see Runtime#totalMemory()
     */
    public long getCommittedHeap() {
        return runtime.totalMemory();
    }

    /**
     * Returns the amount of used memory in the JVM.
     *
     * @return the used memory in bytes
     */
    public long getUsedHeap() {
        return runtime.totalMemory() - runtime.freeMemory();
    }

    /**
     * Returns the amount of free memory in the JVM in bytes.
     *
     * @return the free memory in bytes
     * @see Runtime#freeMemory()
     */
    public long getFreeHeap() {
        return runtime.freeMemory();
    }
}
