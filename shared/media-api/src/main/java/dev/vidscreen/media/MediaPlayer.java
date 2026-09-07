package dev.vidscreen.media;

import java.time.Duration;
import java.util.concurrent.CompletionStage;

public interface MediaPlayer extends AutoCloseable {
    CompletionStage<Void> open(ResolvedMedia media, VideoFrameSink frameSink);

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
