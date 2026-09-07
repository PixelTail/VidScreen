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
            Session session = sessions.get(id);
            if (session == null || !session.media().equals(screen.media())) {
                remove(id);
                session = create(screen);
                sessions.put(id, session);
                resolve(session, screen);
            }
            session.desiredState = screen.playback();
            session.estimatedServerTimeMillis = estimatedServerTimeMillis;
            applyIfReady(session);
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
        return session == null ? null : session.frames();
    }

    private Session create(ScreenState screen) {
        MediaPlayer player = playerFactory.create(width, height);
        return new Session(screen.definition().id(), screen.media(), player, new LatestFrameQueue());
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
            resolver.resolve(new MediaRequest(source, width, height)).whenComplete((resolved, resolveError) -> {
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
        if (!isCurrent(session)) {
            return;
        }
        try {
            session.player().open(resolved, session.frames()).whenComplete((ignored, openError) -> {
                synchronized (ScreenPlaybackCoordinator.this) {
                    if (!isCurrent(session)) {
                        return;
                    }
                    if (openError != null) {
                        failLocked(session, "open", unwrap(openError));
                        return;
                    }
                    session.ready = true;
                    applyIfReady(session);
                }
            });
        } catch (RuntimeException error) {
            failLocked(session, "open", error);
        }
    }

    private void applyIfReady(Session session) {
        if (!session.ready || session.failed || session.desiredState == null) {
            return;
        }
        PlaybackState desired = session.desiredState;
        try {
            if (session.appliedRevision != desired.revision()) {
                long target = desired.targetPositionMillis(session.estimatedServerTimeMillis);
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
        if (desired.status() != PlaybackStatus.PLAYING) {
            return;
        }
        long now = session.estimatedServerTimeMillis;
        long elapsed = now - session.lastDriftCheckMillis;
        if (elapsed >= 0 && elapsed < DRIFT_CHECK_INTERVAL_MILLIS) {
            return;
        }
        session.lastDriftCheckMillis = now;
        long target = desired.targetPositionMillis(now);
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
        session.failed = true;
        try {
            session.player().close();
        } catch (RuntimeException ignored) {
        }
        failureHandler.accept(new PlaybackFailure(session.id(), stage, cause));
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
            session.close();
        }
        sessions.clear();
    }

    private static final class Session implements AutoCloseable {
        private final UUID id;
        private final MediaDescriptor media;
        private final MediaPlayer player;
        private final LatestFrameQueue frames;
        private PlaybackState desiredState;
        private long estimatedServerTimeMillis;
        private long appliedRevision = -1;
        private long lastDriftCheckMillis;
        private double appliedRate = Double.NaN;
        private boolean ready;
        private boolean failed;

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
            try {
                player.close();
            } finally {
                frames.close();
            }
        }
    }
}
