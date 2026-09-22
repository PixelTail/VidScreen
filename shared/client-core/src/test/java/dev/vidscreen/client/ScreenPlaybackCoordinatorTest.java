package dev.vidscreen.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

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
import dev.vidscreen.media.MediaPlayerState;
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

    @Test
    void loopsFromEndUsingKnownDuration() {
        FakePlayer player = new FakePlayer();
        player.durationMillis = 10_000;
        ScreenPlaybackCoordinator coordinator = coordinator(player);
        ScreenState state = screen(PlaybackStatus.PLAYING, 6, true);

        coordinator.reconcile(Collections.singletonList(state), 12_000);
        player.state = MediaPlayerState.ENDED;

        coordinator.reconcile(Collections.singletonList(state), 13_000);

        assertEquals(Duration.ofMillis(8_000), player.seekPosition);
        assertEquals(2, player.playCount.get());
        coordinator.close();
    }

    @Test
    void lateJoinAndDriftCorrectionStayInsideTheLoopDuration() {
        FakePlayer player = new FakePlayer();
        player.durationMillis = 10_000;
        ScreenPlaybackCoordinator coordinator = coordinator(player);
        ScreenState state = screen(PlaybackStatus.PLAYING, 6, true);
        coordinator.reconcile(Collections.singletonList(state), 27_000);
        assertEquals(Duration.ofMillis(2_000), player.seekPosition);
        coordinator.reconcile(Collections.singletonList(state), 29_000);
        assertEquals(Duration.ofMillis(4_000), player.seekPosition);
        coordinator.close();
    }

    @Test
    void doesNotKeepSeekingAfterNonLoopingEndOfStream() {
        FakePlayer player = new FakePlayer();
        player.durationMillis = 10_000;
        ScreenPlaybackCoordinator coordinator = coordinator(player);
        ScreenState state = screen(PlaybackStatus.PLAYING, 7, false);

        coordinator.reconcile(Collections.singletonList(state), 12_000);
        player.state = MediaPlayerState.ENDED;
        coordinator.reconcile(Collections.singletonList(state), 13_000);
        int seeksAtEnd = player.seekCount.get();

        coordinator.reconcile(Collections.singletonList(state), 15_000);

        assertEquals(seeksAtEnd, player.seekCount.get());
        coordinator.reconcile(Collections.singletonList(screen(PlaybackStatus.PLAYING, 8, false)), 16_000);
        assertEquals(2, player.playCount.get());
        coordinator.close();
    }

    @Test
    void reportsPostOpenFailureAndRetriesOnlyForNewRevision() {
        FakePlayer player = new FakePlayer();
        AtomicInteger failures = new AtomicInteger();
        ScreenPlaybackCoordinator coordinator = coordinator(
                player,
                resolver(CompletableFuture.completedFuture(
                        new ResolvedMedia(URI.create("https://media.example/video.mp4"), MediaKind.MP4, null))),
                failure -> failures.incrementAndGet());
        ScreenState state = screen(PlaybackStatus.PLAYING, 8, false);

        coordinator.reconcile(Collections.singletonList(state), 12_000);
        player.failure = new IllegalStateException("decoder failed");
        player.state = MediaPlayerState.FAILED;
        coordinator.reconcile(Collections.singletonList(state), 12_100);

        assertEquals(1, failures.get());
        assertNull(coordinator.frameQueue(state.definition().id()));
        coordinator.reconcile(Collections.singletonList(state), 13_000);
        assertEquals(1, player.openCount.get());

        ScreenState nextRevision = screen(PlaybackStatus.PLAYING, 9, false);
        coordinator.reconcile(Collections.singletonList(nextRevision), 14_000);
        assertEquals(2, player.openCount.get());
        coordinator.close();
    }

    @Test
    void doesNotOpenAfterResolverCompletesForRemovedScreen() {
        FakePlayer player = new FakePlayer();
        CompletableFuture<ResolvedMedia> pending = new CompletableFuture<ResolvedMedia>();
        ScreenPlaybackCoordinator coordinator = coordinator(player, resolver(pending));
        ScreenState state = screen(PlaybackStatus.PLAYING, 10, false);

        coordinator.reconcile(Collections.singletonList(state), 12_000);
        coordinator.reconcile(Collections.<ScreenState>emptyList(), 12_100);
        pending.complete(new ResolvedMedia(URI.create("https://media.example/video.mp4"), MediaKind.MP4, null));

        assertEquals(0, player.openCount.get());
        assertNull(coordinator.frameQueue(state.definition().id()));
        coordinator.close();
    }

    @Test
    void constructionFailureIsRetainedUntilAuthoritativeRevisionChanges() {
        AtomicInteger creations = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();
        MediaPlayerFactory factory = (width, height) -> {
            creations.incrementAndGet();
            throw new IllegalStateException("factory failed");
        };
        MediaResolver resolver = resolver(CompletableFuture.completedFuture(
                new ResolvedMedia(URI.create("https://media.example/video.mp4"), MediaKind.MP4, null)));
        ScreenPlaybackCoordinator coordinator = new ScreenPlaybackCoordinator(
                new MediaResolverRegistry(Collections.singletonList(resolver)),
                factory,
                failure -> failures.incrementAndGet(),
                640,
                360);
        ScreenState state = screen(PlaybackStatus.PLAYING, 11, false);

        coordinator.reconcile(Collections.singletonList(state), 12_000);
        coordinator.reconcile(Collections.singletonList(state), 13_000);
        assertEquals(1, creations.get());
        assertEquals(1, failures.get());

        coordinator.reconcile(Collections.singletonList(screen(PlaybackStatus.PLAYING, 12, false)), 14_000);
        assertEquals(2, creations.get());
        assertEquals(2, failures.get());
        coordinator.close();
    }

    private static ScreenPlaybackCoordinator coordinator(FakePlayer player) {
        return coordinator(player, resolver(CompletableFuture.completedFuture(
                new ResolvedMedia(URI.create("https://media.example/video.mp4"), MediaKind.MP4, null))));
    }

    private static ScreenPlaybackCoordinator coordinator(FakePlayer player, MediaResolver resolver) {
        return coordinator(player, resolver, failure -> { throw new AssertionError(failure.cause()); });
    }

    private static ScreenPlaybackCoordinator coordinator(
            FakePlayer player,
            MediaResolver resolver,
            java.util.function.Consumer<PlaybackFailure> failureHandler) {
        return new ScreenPlaybackCoordinator(
                new MediaResolverRegistry(Collections.singletonList(resolver)),
                (width, height) -> player,
                failureHandler,
                640,
                360);
    }

    private static MediaResolver resolver(CompletionStage<ResolvedMedia> result) {
        return new MediaResolver() {
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
                return result;
            }
        };
    }

    private static ScreenState screen(PlaybackStatus status, long revision) {
        return screen(status, revision, false);
    }

    private static ScreenState screen(PlaybackStatus status, long revision, boolean looping) {
        UUID id = UUID.fromString("01234567-89ab-cdef-0123-456789abcdef");
        ScreenDefinition definition = new ScreenDefinition(
                id,
                "screen",
                new DimensionKey("minecraft:overworld"),
                ScreenGeometry.between(new BlockPoint(0, 64, 0), new BlockPoint(2, 65, 0), Facing.NORTH),
                ScreenFit.CONTAIN,
                96);
        PlaybackState playback = new PlaybackState(revision, status, 5_000, 10_000, 1.0, looping);
        return new ScreenState(revision, definition,
                new MediaDescriptor("direct", "https://media.example/video.mp4"), playback);
    }

    private static final class FakePlayer implements MediaPlayer {
        private final AtomicInteger openCount = new AtomicInteger();
        private final AtomicInteger playCount = new AtomicInteger();
        private final AtomicInteger pauseCount = new AtomicInteger();
        private final AtomicInteger requestFrameCount = new AtomicInteger();
        private final AtomicInteger closeCount = new AtomicInteger();
        private final AtomicInteger seekCount = new AtomicInteger();
        private Duration seekPosition;
        private long durationMillis = -1;
        private MediaPlayerState state = MediaPlayerState.READY;
        private Throwable failure;

        @Override
        public CompletionStage<Void> open(ResolvedMedia media, VideoFrameSink frameSink) {
            openCount.incrementAndGet();
            state = MediaPlayerState.READY;
            failure = null;
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void play() {
            playCount.incrementAndGet();
            state = MediaPlayerState.PLAYING;
        }

        @Override
        public void pause() {
            pauseCount.incrementAndGet();
            state = MediaPlayerState.PAUSED;
        }

        @Override
        public void seek(Duration position) {
            seekPosition = position;
            seekCount.incrementAndGet();
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
        public MediaPlayerState state() {
            return state;
        }

        @Override
        public long durationMillis() {
            return durationMillis;
        }

        @Override
        public Throwable failure() {
            return failure;
        }

        @Override
        public void close() {
            closeCount.incrementAndGet();
        }
    }
}
