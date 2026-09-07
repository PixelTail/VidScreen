package dev.vidscreen.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.net.URI;
import java.time.Duration;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import dev.vidscreen.domain.BlockPoint;
import dev.vidscreen.domain.DimensionKey;
import dev.vidscreen.domain.Facing;
import dev.vidscreen.domain.MediaDescriptor;
import dev.vidscreen.domain.PlaybackState;
import dev.vidscreen.domain.PlaybackStatus;
import dev.vidscreen.domain.ScreenDefinition;
import dev.vidscreen.domain.ScreenFit;
import dev.vidscreen.domain.ScreenGeometry;
import dev.vidscreen.domain.ScreenState;
import dev.vidscreen.media.MediaKind;
import dev.vidscreen.media.MediaPlayer;
import dev.vidscreen.media.MediaRequest;
import dev.vidscreen.media.MediaResolver;
import dev.vidscreen.media.ResolvedMedia;
import dev.vidscreen.media.VideoFrameSink;

class ScreenPlaybackCoordinatorTest {
    @Test
    void opensAppliesCorrectsAndClosesPlaybackSession() {
        FakePlayer player = new FakePlayer();
        ScreenPlaybackCoordinator coordinator = coordinator(player);
        ScreenState state = screen(PlaybackStatus.PLAYING, 5);

        coordinator.reconcile(Collections.singletonList(state), 12_000);

        assertEquals(1, player.openCount.get());
        assertEquals(1, player.playCount.get());
        assertEquals(Duration.ofMillis(7_000), player.seekPosition);
        assertNotNull(coordinator.frameQueue(state.definition().id()));

        coordinator.reconcile(Collections.singletonList(state), 13_100);
        assertEquals(Duration.ofMillis(8_100), player.seekPosition);
        assertEquals(1, player.playCount.get());

        coordinator.reconcile(Collections.<ScreenState>emptyList(), 13_100);
        assertEquals(1, player.closeCount.get());
    }

    @Test
    void requestsPreviewFrameForPausedState() {
        FakePlayer player = new FakePlayer();
        ScreenPlaybackCoordinator coordinator = coordinator(player);

        coordinator.reconcile(Collections.singletonList(screen(PlaybackStatus.PAUSED, 6)), 12_000);

        assertEquals(1, player.pauseCount.get());
        assertEquals(1, player.requestFrameCount.get());
        assertEquals(0, player.playCount.get());
        coordinator.close();
    }

    private static ScreenPlaybackCoordinator coordinator(FakePlayer player) {
        MediaResolver resolver = new MediaResolver() {
            @Override
            public String id() {
                return "direct";
            }

            @Override
            public boolean supports(URI source) {
                return true;
            }

            @Override
            public CompletionStage<ResolvedMedia> resolve(MediaRequest request) {
                return CompletableFuture.completedFuture(new ResolvedMedia(request.source(), MediaKind.MP4, null));
            }
        };
        return new ScreenPlaybackCoordinator(
                new MediaResolverRegistry(Collections.singletonList(resolver)),
                (width, height) -> player,
                failure -> { throw new AssertionError(failure.cause()); },
                640,
                360);
    }

    private static ScreenState screen(PlaybackStatus status, long revision) {
        UUID id = UUID.fromString("01234567-89ab-cdef-0123-456789abcdef");
        ScreenDefinition definition = new ScreenDefinition(
                id,
                "screen",
                new DimensionKey("minecraft:overworld"),
                ScreenGeometry.between(new BlockPoint(0, 64, 0), new BlockPoint(2, 65, 0), Facing.NORTH),
                ScreenFit.CONTAIN,
                96);
        PlaybackState playback = new PlaybackState(revision, status, 5_000, 10_000, 1.0, false);
        return new ScreenState(revision, definition,
                new MediaDescriptor("direct", "https://media.example/video.mp4"), playback);
    }

    private static final class FakePlayer implements MediaPlayer {
        private final AtomicInteger openCount = new AtomicInteger();
        private final AtomicInteger playCount = new AtomicInteger();
        private final AtomicInteger pauseCount = new AtomicInteger();
        private final AtomicInteger requestFrameCount = new AtomicInteger();
        private final AtomicInteger closeCount = new AtomicInteger();
        private Duration seekPosition;

        @Override
        public CompletionStage<Void> open(ResolvedMedia media, VideoFrameSink frameSink) {
            openCount.incrementAndGet();
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void play() {
            playCount.incrementAndGet();
        }

        @Override
        public void pause() {
            pauseCount.incrementAndGet();
        }

        @Override
        public void seek(Duration position) {
            seekPosition = position;
        }

        @Override
        public void requestFrame() {
            requestFrameCount.incrementAndGet();
        }

        @Override
        public Duration position() {
            return seekPosition == null ? Duration.ZERO : seekPosition;
        }

        @Override
        public void setRate(double rate) {
        }

        @Override
        public void close() {
            closeCount.incrementAndGet();
        }
    }
}
