package dev.vidscreen.media;

import java.time.Duration;
import java.util.concurrent.CompletionStage;

public interface MediaPlayer extends AutoCloseable {
    CompletionStage<Void> open(ResolvedMedia media, VideoFrameSink frameSink);

    /**
     * Returns the latest decoder state.  Implementations should update this
     * state when a decoder thread reaches end-of-stream or fails after open.
     */
    default MediaPlayerState state() {
        return MediaPlayerState.UNKNOWN;
    }

    /**
     * Returns the known media duration in milliseconds, or {@code -1} when
     * the source does not expose a finite duration.
     */
    default long durationMillis() {
        return -1L;
    }

    /**
     * Returns the terminal decoder failure when {@link #state()} is
     * {@link MediaPlayerState#FAILED}, otherwise {@code null}.
     */
    default Throwable failure() {
        return null;
    }

    void play();

    void pause();

    void seek(Duration position);

    default void requestFrame() {
    }

    Duration position();

    default boolean supportsDynamicRateAdjustment() {
        return false;
    }

    void setRate(double rate);

    @Override
    void close();
}
