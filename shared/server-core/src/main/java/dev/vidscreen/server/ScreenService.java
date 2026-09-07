package dev.vidscreen.server;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import dev.vidscreen.domain.MediaDescriptor;
import dev.vidscreen.domain.PlaybackState;
import dev.vidscreen.domain.PlaybackStatus;
import dev.vidscreen.domain.ScreenDefinition;
import dev.vidscreen.domain.ScreenState;

public final class ScreenService {
    private final ScreenRepository repository;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final Map<UUID, ScreenState> screens = new LinkedHashMap<UUID, ScreenState>();
    private long revision;

    public ScreenService(ScreenRepository repository) {
        this.repository = repository;
    }

    public void load() throws IOException {
        Collection<ScreenState> loaded = repository.load();
        lock.writeLock().lock();
        try {
            screens.clear();
            revision = 0;
            for (ScreenState screen : loaded) {
                if (screens.put(screen.definition().id(), screen) != null) {
                    throw new IOException("Duplicate screen ID in persistence: " + screen.definition().id());
                }
                revision = Math.max(revision, screen.revision());
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public long revision() {
        lock.readLock().lock();
        try {
            return revision;
        } finally {
            lock.readLock().unlock();
        }
    }

    public Collection<ScreenState> snapshot() {
        lock.readLock().lock();
        try {
            return Collections.unmodifiableList(new ArrayList<ScreenState>(screens.values()));
        } finally {
            lock.readLock().unlock();
        }
    }

    public ScreenState find(UUID id) {
        lock.readLock().lock();
        try {
            return screens.get(id);
        } finally {
            lock.readLock().unlock();
        }
    }

    public ScreenState findByName(String name) {
        String normalized = name.toLowerCase(Locale.ROOT);
        lock.readLock().lock();
        try {
            for (ScreenState screen : screens.values()) {
                if (screen.definition().name().equals(normalized)) {
                    return screen;
                }
            }
            return null;
        } finally {
            lock.readLock().unlock();
        }
    }

    public ScreenState create(ScreenDefinition definition, long serverTimeMillis)
            throws IOException, ScreenConflictException {
        lock.writeLock().lock();
        try {
            if (screens.containsKey(definition.id()) || findByNameWithoutLock(definition.name()) != null) {
                throw new ScreenConflictException("A screen with that ID or name already exists");
            }
            long nextRevision = nextRevision();
            ScreenState created = new ScreenState(
                    nextRevision,
                    definition,
                    null,
                    PlaybackState.stopped(nextRevision, serverTimeMillis));
            Map<UUID, ScreenState> next = copyScreens();
            next.put(definition.id(), created);
            commit(nextRevision, next);
            return created;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public ScreenState setMedia(UUID id, MediaDescriptor media, long serverTimeMillis) throws IOException {
        lock.writeLock().lock();
        try {
            ScreenState current = requireScreen(id);
            long nextRevision = nextRevision();
            PlaybackState stopped = PlaybackState.stopped(nextRevision, serverTimeMillis);
            ScreenState changed = current.withMedia(nextRevision, media, stopped);
            Map<UUID, ScreenState> next = copyScreens();
            next.put(id, changed);
            commit(nextRevision, next);
            return changed;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public ScreenState setPlayback(
            UUID id,
            PlaybackStatus status,
            long positionMillis,
            long serverTimeMillis) throws IOException {
        lock.writeLock().lock();
        try {
            ScreenState current = requireScreen(id);
            if (status == PlaybackStatus.PLAYING && current.media() == null) {
                throw new IllegalStateException("Cannot play a screen without media");
            }
            long nextRevision = nextRevision();
            PlaybackState playback = new PlaybackState(
                    nextRevision,
                    status,
                    positionMillis,
                    serverTimeMillis,
                    current.playback().playbackRate(),
                    current.playback().looping());
            ScreenState changed = current.withPlayback(nextRevision, playback);
            Map<UUID, ScreenState> next = copyScreens();
            next.put(id, changed);
            commit(nextRevision, next);
            return changed;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public ScreenState setRateAndLoop(
            UUID id,
            double rate,
            boolean looping,
            long serverTimeMillis) throws IOException {
        lock.writeLock().lock();
        try {
            ScreenState current = requireScreen(id);
            long nextRevision = nextRevision();
            PlaybackState playback = current.playback().withRateAndLoop(nextRevision, rate, looping, serverTimeMillis);
            ScreenState changed = current.withPlayback(nextRevision, playback);
            Map<UUID, ScreenState> next = copyScreens();
            next.put(id, changed);
            commit(nextRevision, next);
            return changed;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public ScreenState delete(UUID id) throws IOException {
        lock.writeLock().lock();
        try {
            ScreenState current = requireScreen(id);
            long nextRevision = nextRevision();
            Map<UUID, ScreenState> next = copyScreens();
            next.remove(id);
            commit(nextRevision, next);
            return current;
        } finally {
            lock.writeLock().unlock();
        }
    }

    private ScreenState findByNameWithoutLock(String name) {
        String normalized = name.toLowerCase(Locale.ROOT);
        for (ScreenState screen : screens.values()) {
            if (screen.definition().name().equals(normalized)) {
                return screen;
            }
        }
        return null;
    }

    private ScreenState requireScreen(UUID id) {
        ScreenState screen = screens.get(id);
        if (screen == null) {
            throw new IllegalArgumentException("Unknown screen: " + id);
        }
        return screen;
    }

    private long nextRevision() {
        if (revision == Long.MAX_VALUE) {
            throw new IllegalStateException("Screen revision exhausted");
        }
        return revision + 1;
    }

    private Map<UUID, ScreenState> copyScreens() {
        return new LinkedHashMap<UUID, ScreenState>(screens);
    }

    private void commit(long nextRevision, Map<UUID, ScreenState> next) throws IOException {
        repository.save(nextRevision, next.values());
        screens.clear();
        screens.putAll(next);
        revision = nextRevision;
    }
}
