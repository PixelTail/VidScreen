package dev.vidscreen.media;

/**
 * Observed state of a media player.
 *
 * <p>The state is intentionally polled by the client coordinator.  A player
 * may finish or fail on its decoder thread after {@link MediaPlayer#open}
 * has completed, so an open future alone cannot describe the complete
 * playback lifecycle.</p>
 */
public enum MediaPlayerState {
    UNKNOWN,
    OPENING,
    READY,
    PLAYING,
    PAUSED,
    ENDED,
    FAILED,
    CLOSED
}
