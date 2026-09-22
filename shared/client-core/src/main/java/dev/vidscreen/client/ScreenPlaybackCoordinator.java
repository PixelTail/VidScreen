package dev.vidscreen.client;

import java.net.URI;
import java.time.Duration;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Future;
import java.util.function.Consumer;

import dev.vidscreen.domain.DriftAction;
import dev.vidscreen.domain.DriftCorrection;
import dev.vidscreen.domain.DriftPolicy;
import dev.vidscreen.domain.MediaDescriptor;
import dev.vidscreen.domain.PlaybackState;
import dev.vidscreen.domain.PlaybackStatus;
import dev.vidscreen.domain.ScreenState;
import dev.vidscreen.media.LatestFrameQueue;
import dev.vidscreen.media.MediaPlayer;
import dev.vidscreen.media.MediaPlayerState;
import dev.vidscreen.media.MediaRequest;
import dev.vidscreen.media.MediaResolver;
import dev.vidscreen.media.ResolvedMedia;

public final class ScreenPlaybackCoordinator implements AutoCloseable {
    private static final long DRIFT_CHECK_INTERVAL_MILLIS = 1_000;

    private final MediaResolverRegistry resolvers;
    private final MediaPlayerFactory playerFactory;
    private final Consumer<PlaybackFailure> failureHandler;
    private final DriftPolicy driftPolicy;
    private final int width;
    private final int height;
    private final Map<UUID, Session> sessions = new LinkedHashMap<UUID, Session>();
    private boolean closed;

    public ScreenPlaybackCoordinator(
            MediaResolverRegistry resolvers,
            MediaPlayerFactory playerFactory,
            Consumer<PlaybackFailure> failureHandler,
            int width,
            int height) {
        this(resolvers, playerFactory, failureHandler, width, height,
                new DriftPolicy(80, 750, 0.05, 5_000));
    }

    public ScreenPlaybackCoordinator(
            MediaResolverRegistry resolvers,
            MediaPlayerFactory playerFactory,
            Consumer<PlaybackFailure> failureHandler,
            int width,
            int height,
            DriftPolicy driftPolicy) {
        this.resolvers = resolvers;
        this.playerFactory = playerFactory;
        this.failureHandler = failureHandler;
        this.width = width;
        this.height = height;
        this.driftPolicy = driftPolicy;
    }

    public synchronized void reconcile(Collection<ScreenState> screens, long estimatedServerTimeMillis) {
        requireOpen();
        Set<UUID> active = new HashSet<UUID>();
        for (ScreenState screen : screens) {
            UUID id = screen.definition().id();
            if (screen.media() == null) {
                remove(id);
                continue;
            }
            active.add(id);

            PlaybackState desired = screen.playback();
            Session session = sessions.get(id);
            boolean sourceChanged = session == null || !session.media().equals(screen.media());
            boolean authoritativeRetry = session != null
                    && session.failed
                    && desired.revision() > session.failedRevision;
            if (sourceChanged || authoritativeRetry) {
                remove(id);
                session = create(screen, estimatedServerTimeMillis);
                sessions.put(id, session);
                if (!session.failed) {
                    resolve(session, screen);
                }
            }

            session.desiredState = desired;
            session.estimatedServerTimeMillis = estimatedServerTimeMillis;
            if (!session.failed) {
                observeTerminal(session);
                applyIfReady(session);
            }
        }

        UUID[] ids = sessions.keySet().toArray(new UUID[0]);
        for (UUID id : ids) {
            if (!active.contains(id)) {
                remove(id);
            }
        }
    }

    public synchronized LatestFrameQueue frameQueue(UUID screenId) {
        Session session = sessions.get(screenId);
        return session == null || session.failed ? null : session.frames();
    }

    private Session create(ScreenState screen, long estimatedServerTimeMillis) {
        LatestFrameQueue frames = new LatestFrameQueue();
        MediaPlayer player;
        try {
            player = playerFactory.create(width, height);
            if (player == null) {
                throw new NullPointerException("Media player factory returned null");
            }
        } catch (RuntimeException | LinkageError error) {
            Session failed = new Session(screen.definition().id(), screen.media(), null, frames);
            failed.desiredState = screen.playback();
            failed.estimatedServerTimeMillis = estimatedServerTimeMillis;
            failed.failed = true;
            failed.failedRevision = screen.playback().revision();
            frames.close();
            notifyFailure(new PlaybackFailure(failed.id(), "create", error));
            return failed;
        }

        Session session = new Session(screen.definition().id(), screen.media(), player, frames);
        session.desiredState = screen.playback();
        session.estimatedServerTimeMillis = estimatedServerTimeMillis;
        return session;
    }

    private void resolve(Session session, ScreenState screen) {
        MediaResolver resolver;
        URI source;
        try {
            resolver = resolvers.require(screen.media().resolverId());
            source = URI.create(screen.media().source());
            if (!resolver.supports(source)) {
                throw new IllegalArgumentException("Resolver " + resolver.id() + " does not support this source");
            }
        } catch (RuntimeException error) {
            fail(session, "resolve", error);
            return;
        }

        try {
            CompletionStage<ResolvedMedia> resolution = resolver.resolve(new MediaRequest(source, width, height));
            if (resolution == null) {
                throw new IllegalStateException("Media resolver returned no completion stage");
            }
            session.resolution = resolution;
            resolution.whenComplete((resolved, resolveError) -> {
                synchronized (ScreenPlaybackCoordinator.this) {
                    if (!isCurrent(session)) {
                        return;
                    }
                    session.resolution = null;
                }
                if (resolveError != null) {
                    fail(session, "resolve", unwrap(resolveError));
                    return;
                }
                if (resolved == null) {
                    fail(session, "resolve", new IllegalStateException("Media resolver returned no result"));
                    return;
                }
                open(session, resolved);
            });
        } catch (RuntimeException error) {
            fail(session, "resolve", error);
        }
    }

    private synchronized void open(Session session, ResolvedMedia resolved) {
        if (!isCurrent(session) || session.failed || session.player() == null) {
            return;
        }
        try {
            CompletionStage<Void> opening = session.player().open(resolved, session.frames());
            if (opening == null) {
                throw new IllegalStateException("Media player returned no open completion stage");
            }
            opening.whenComplete((ignored, openError) -> {
                synchronized (ScreenPlaybackCoordinator.this) {
                    if (!isCurrent(session)) {
                        return;
                    }
                    if (openError != null) {
                        failLocked(session, "open", unwrap(openError));
                        return;
                    }
                    session.ready = true;
                    session.durationMillis = knownDurationMillis(session.player());
                    applyIfReady(session);
                }
            });
        } catch (RuntimeException error) {
            failLocked(session, "open", error);
        }
    }

    private void observeTerminal(Session session) {
        if (session.failed || session.player() == null) {
            return;
        }
        try {
            long durationMillis = knownDurationMillis(session.player());
            if (durationMillis > 0) {
                session.durationMillis = durationMillis;
            }
            MediaPlayerState state = session.player().state();
            if (state == MediaPlayerState.FAILED) {
                Throwable cause = session.player().failure();
                failLocked(session, "decode", cause == null
                        ? new IllegalStateException("Media decoder failed")
                        : cause);
            } else if (state == MediaPlayerState.ENDED) {
                if (session.ended && session.endedRevision != session.desiredState.revision()) {
                    session.ended = false;
                    session.endedRevision = -1;
                    return;
                }
                handleEndOfStream(session);
            }
        } catch (RuntimeException error) {
            failLocked(session, "status", error);
        }
    }

    private void handleEndOfStream(Session session) {
        PlaybackState desired = session.desiredState;
        if (desired == null) {
            return;
        }
        if (desired.status() == PlaybackStatus.PLAYING && desired.looping() && session.durationMillis > 0) {
            long target = desired.targetPositionMillis(session.estimatedServerTimeMillis);
            long loopPosition = target % session.durationMillis;
            try {
                setRate(session, desired.playbackRate());
                session.player().seek(Duration.ofMillis(loopPosition));
                session.player().play();
                session.ended = false;
                session.endedRevision = -1;
                session.appliedRevision = desired.revision();
                session.lastDriftCheckMillis = session.estimatedServerTimeMillis;
            } catch (RuntimeException error) {
                failLocked(session, "loop", error);
            }
            return;
        }
        session.ended = true;
        session.endedRevision = desired.revision();
    }

    private void applyIfReady(Session session) {
        if (!session.ready || session.failed || session.desiredState == null || session.player() == null) {
            return;
        }
        PlaybackState desired = session.desiredState;
        if (session.ended && session.endedRevision == desired.revision()) {
            return;
        }
        try {
            if (session.appliedRevision != desired.revision()) {
                session.ended = false;
                session.endedRevision = -1;
                long target = targetPositionMillis(session, desired);
                setRate(session, desired.playbackRate());
                session.player().seek(Duration.ofMillis(target));
                if (desired.status() == PlaybackStatus.PLAYING) {
                    session.player().play();
                } else {
                    session.player().pause();
                    session.player().requestFrame();
                }
                session.appliedRevision = desired.revision();
                session.lastDriftCheckMillis = session.estimatedServerTimeMillis;
                return;
            }
            correctDrift(session, desired);
        } catch (RuntimeException error) {
            failLocked(session, "playback", error);
        }
    }

    private void correctDrift(Session session, PlaybackState desired) {
        if (session.ended && session.endedRevision == desired.revision()) {
            return;
        }
        if (desired.status() != PlaybackStatus.PLAYING) {
            return;
        }
        long now = session.estimatedServerTimeMillis;
        long elapsed = now - session.lastDriftCheckMillis;
        if (elapsed >= 0 && elapsed < DRIFT_CHECK_INTERVAL_MILLIS) {
            return;
        }
        session.lastDriftCheckMillis = now;
        long target = targetPositionMillis(session, desired);
        long current = session.player().position().toMillis();
        DriftCorrection correction = driftPolicy.correct(current, target, desired.playbackRate());
        if (correction.action() == DriftAction.SEEK) {
            setRate(session, desired.playbackRate());
            session.player().seek(Duration.ofMillis(correction.targetPositionMillis()));
        } else if (correction.action() == DriftAction.ADJUST_RATE
                && session.player().supportsDynamicRateAdjustment()) {
            setRate(session, correction.temporaryRate());
        } else {
            setRate(session, desired.playbackRate());
        }
    }

    private static long knownDurationMillis(MediaPlayer player) {
        long durationMillis = player.durationMillis();
        return durationMillis > 0 ? durationMillis : -1L;
    }

    private static long targetPositionMillis(Session session, PlaybackState desired) {
        long target = desired.targetPositionMillis(session.estimatedServerTimeMillis);
        return desired.looping() && session.durationMillis > 0 ? target % session.durationMillis : target;
    }

    private static void setRate(Session session, double requestedRate) {
        double boundedRate = Math.max(0.25, Math.min(4.0, requestedRate));
        if (!Double.isNaN(session.appliedRate) && Math.abs(session.appliedRate - boundedRate) < 0.0001) {
            return;
        }
        session.player().setRate(boundedRate);
        session.appliedRate = boundedRate;
    }

    private synchronized void fail(Session session, String stage, Throwable cause) {
        if (isCurrent(session)) {
            failLocked(session, stage, cause);
        }
    }

    private void failLocked(Session session, String stage, Throwable cause) {
        if (session.failed) {
            return;
        }
        session.failed = true;
        session.ready = false;
        session.failedRevision = session.desiredState == null ? -1 : session.desiredState.revision();
        try {
            if (session.player() != null) {
                session.player().close();
            }
        } catch (RuntimeException ignored) {
        } finally {
            session.frames().close();
        }
        notifyFailure(new PlaybackFailure(session.id(), stage, cause));
    }

    private void notifyFailure(PlaybackFailure failure) {
        try {
            failureHandler.accept(failure);
        } catch (RuntimeException ignored) {
            // A diagnostic callback must not take down the client tick thread.
        }
    }

    private boolean isCurrent(Session session) {
        return !closed && sessions.get(session.id()) == session;
    }

    private void remove(UUID id) {
        Session removed = sessions.remove(id);
        if (removed != null) {
            removed.close();
        }
    }

    private static Throwable unwrap(Throwable error) {
        return error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
    }

    private static void cancel(CompletionStage<?> stage) {
        if (stage instanceof Future<?>) {
            ((Future<?>) stage).cancel(true);
        }
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("Playback coordinator is closed");
        }
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        for (Session session : sessions.values()) {
            try {
                session.close();
            } catch (RuntimeException ignored) {
            }
        }
        sessions.clear();
    }

    private static final class Session implements AutoCloseable {
        private final UUID id;
        private final MediaDescriptor media;
        private final MediaPlayer player;
        private final LatestFrameQueue frames;
        private CompletionStage<?> resolution;
        private PlaybackState desiredState;
        private long estimatedServerTimeMillis;
        private long appliedRevision = -1;
        private long failedRevision = -1;
        private long endedRevision = -1;
        private long lastDriftCheckMillis;
        private long durationMillis = -1;
        private double appliedRate = Double.NaN;
        private boolean ready;
        private boolean failed;
        private boolean ended;

        private Session(UUID id, MediaDescriptor media, MediaPlayer player, LatestFrameQueue frames) {
            this.id = id;
            this.media = media;
            this.player = player;
            this.frames = frames;
        }

        UUID id() {
            return id;
        }

        MediaDescriptor media() {
            return media;
        }

        MediaPlayer player() {
            return player;
        }

        LatestFrameQueue frames() {
            return frames;
        }

        @Override
        public void close() {
            cancel(resolution);
            resolution = null;
            try {
                if (player != null) {
                    player.close();
                }
            } finally {
                frames.close();
            }
        }
    }
}
