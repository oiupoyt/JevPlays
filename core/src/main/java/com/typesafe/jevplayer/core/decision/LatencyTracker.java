package com.typesafe.jevplayer.core.decision;

import java.util.Arrays;

public final class LatencyTracker {
    private static final int WINDOW_SIZE = 64;
    private final long[] samples = new long[WINDOW_SIZE];
    private int index = 0;
    private int count = 0;
    private long totalRequests = 0;
    private long totalErrors = 0;

    public synchronized void recordLatency(long latencyMs) {
        samples[index] = latencyMs;
        index = (index + 1) % WINDOW_SIZE;
        if (count < WINDOW_SIZE) count++;
        totalRequests++;
    }

    public synchronized void recordError() {
        totalRequests++;
        totalErrors++;
    }

    public synchronized long getP50() {
        if (count == 0) return 0;
        long[] copy = Arrays.copyOf(samples, count);
        Arrays.sort(copy);
        return copy[copy.length / 2];
    }

    public synchronized long getP95() {
        if (count == 0) return 0;
        long[] copy = Arrays.copyOf(samples, count);
        Arrays.sort(copy);
        int p95Index = (int) Math.ceil(copy.length * 0.95) - 1;
        if (p95Index < 0) p95Index = 0;
        if (p95Index >= copy.length) p95Index = copy.length - 1;
        return copy[p95Index];
    }

    public synchronized double getErrorRate() {
        if (totalRequests == 0) return 0.0;
        return (double) totalErrors / totalRequests;
    }

    public synchronized long getTotalRequests() {
        return totalRequests;
    }

    public synchronized long getTotalErrors() {
        return totalErrors;
    }
}
