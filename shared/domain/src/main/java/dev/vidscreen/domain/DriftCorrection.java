package dev.vidscreen.domain;

public final class DriftCorrection {
    private final DriftAction action;
    private final long targetPositionMillis;
    private final double temporaryRate;

    public DriftCorrection(DriftAction action, long targetPositionMillis, double temporaryRate) {
        this.action = action;
        this.targetPositionMillis = targetPositionMillis;
        this.temporaryRate = temporaryRate;
    }

    public DriftAction action() {
        return action;
    }

    public long targetPositionMillis() {
        return targetPositionMillis;
    }

    public double temporaryRate() {
        return temporaryRate;
    }
}
