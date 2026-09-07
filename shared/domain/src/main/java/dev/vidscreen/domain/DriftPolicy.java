package dev.vidscreen.domain;

public final class DriftPolicy {
    private final long ignoreThresholdMillis;
    private final long seekThresholdMillis;
    private final double maximumRateAdjustment;
    private final long convergenceWindowMillis;

    public DriftPolicy(
            long ignoreThresholdMillis,
            long seekThresholdMillis,
            double maximumRateAdjustment,
            long convergenceWindowMillis) {
        if (ignoreThresholdMillis < 0 || seekThresholdMillis <= ignoreThresholdMillis) {
            throw new IllegalArgumentException("Drift thresholds are invalid");
        }
        if (maximumRateAdjustment <= 0 || maximumRateAdjustment > 0.1) {
            throw new IllegalArgumentException("maximumRateAdjustment must be in (0, 0.1]");
        }
        if (convergenceWindowMillis < 1_000) {
            throw new IllegalArgumentException("convergenceWindowMillis must be at least 1000");
        }
        this.ignoreThresholdMillis = ignoreThresholdMillis;
        this.seekThresholdMillis = seekThresholdMillis;
        this.maximumRateAdjustment = maximumRateAdjustment;
        this.convergenceWindowMillis = convergenceWindowMillis;
    }

    public DriftCorrection correct(long currentPositionMillis, long targetPositionMillis, double baseRate) {
        long drift = targetPositionMillis - currentPositionMillis;
        long magnitude = drift == Long.MIN_VALUE ? Long.MAX_VALUE : Math.abs(drift);
        if (magnitude <= ignoreThresholdMillis) {
            return new DriftCorrection(DriftAction.IGNORE, targetPositionMillis, baseRate);
        }
        if (magnitude >= seekThresholdMillis) {
            return new DriftCorrection(DriftAction.SEEK, targetPositionMillis, baseRate);
        }

        double requestedAdjustment = drift / (double) convergenceWindowMillis;
        double boundedAdjustment = Math.max(-maximumRateAdjustment,
                Math.min(maximumRateAdjustment, requestedAdjustment));
        return new DriftCorrection(DriftAction.ADJUST_RATE, targetPositionMillis, baseRate + boundedAdjustment);
    }
}
