package dev.vidscreen.testkit;

import java.util.concurrent.atomic.AtomicLong;

public final class FakeClock {
    private final AtomicLong currentTimeMillis;

    public FakeClock(long initialTimeMillis) {
        currentTimeMillis = new AtomicLong(initialTimeMillis);
    }

    public long nowMillis() {
        return currentTimeMillis.get();
    }

    public long advanceMillis(long deltaMillis) {
        if (deltaMillis < 0) {
            throw new IllegalArgumentException("deltaMillis must be non-negative");
        }
        return currentTimeMillis.addAndGet(deltaMillis);
    }

    public void setMillis(long value) {
        currentTimeMillis.set(value);
    }
}
