package dev.vidscreen.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SynchronizationTest {
    @Test
    void estimatorUsesLowestRoundTripSample() {
        ClockOffsetEstimator estimator = new ClockOffsetEstimator(4);
        estimator.add(new ClockSample(1_000, 1_120, 1_130, 1_260));
        estimator.add(new ClockSample(2_000, 2_105, 2_110, 2_215));

        assertEquals(210, new ClockSample(2_000, 2_105, 2_110, 2_215).roundTripMillis());
        assertEquals(0, estimator.estimateServerOffsetMillis());
    }

    @Test
    void driftPolicyIgnoresAdjustsAndSeeks() {
        DriftPolicy policy = new DriftPolicy(50, 500, 0.02, 10_000);

        assertEquals(DriftAction.IGNORE, policy.correct(1_000, 1_030, 1.0).action());
        assertEquals(DriftAction.ADJUST_RATE, policy.correct(1_000, 1_150, 1.0).action());
        assertEquals(1.015, policy.correct(1_000, 1_150, 1.0).temporaryRate(), 0.00001);
        assertEquals(DriftAction.SEEK, policy.correct(1_000, 1_500, 1.0).action());
    }
}
