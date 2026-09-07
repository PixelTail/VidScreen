package dev.vidscreen.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class PlaybackStateTest {
    @Test
    void advancesOnlyWhilePlaying() {
        PlaybackState playing = new PlaybackState(1, PlaybackStatus.PLAYING, 5_000, 10_000, 1.25, false);
        PlaybackState paused = new PlaybackState(2, PlaybackStatus.PAUSED, 5_000, 10_000, 1.25, false);

        assertEquals(7_500, playing.targetPositionMillis(12_000));
        assertEquals(5_000, paused.targetPositionMillis(12_000));
    }

    @Test
    void rejectsNonMonotonicRevision() {
        PlaybackState state = PlaybackState.stopped(3, 1_000);
        assertThrows(IllegalArgumentException.class,
                () -> state.transition(3, PlaybackStatus.PLAYING, 0, 1_000));
    }
}
