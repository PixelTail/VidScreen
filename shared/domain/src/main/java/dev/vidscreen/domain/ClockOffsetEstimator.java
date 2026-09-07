package dev.vidscreen.domain;

import java.util.ArrayDeque;
import java.util.Deque;

public final class ClockOffsetEstimator {
    private final int capacity;
    private final Deque<ClockSample> samples;

    public ClockOffsetEstimator(int capacity) {
        if (capacity < 1 || capacity > 128) {
            throw new IllegalArgumentException("capacity must be between 1 and 128");
        }
        this.capacity = capacity;
        this.samples = new ArrayDeque<ClockSample>(capacity);
    }

    public synchronized void add(ClockSample sample) {
        if (samples.size() == capacity) {
            samples.removeFirst();
        }
        samples.addLast(sample);
    }

    public synchronized boolean hasEstimate() {
        return !samples.isEmpty();
    }

    public synchronized void clear() {
        samples.clear();
    }

    public synchronized long estimateServerOffsetMillis() {
        if (samples.isEmpty()) {
            throw new IllegalStateException("No clock samples are available");
        }
        ClockSample best = null;
        for (ClockSample sample : samples) {
            if (best == null || sample.roundTripMillis() < best.roundTripMillis()) {
                best = sample;
            }
        }
        return best.serverOffsetMillis();
    }

    public synchronized long estimateServerTimeMillis(long clientTimeMillis) {
        return Math.addExact(clientTimeMillis, estimateServerOffsetMillis());
    }
}
