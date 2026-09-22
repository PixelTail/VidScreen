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
import dev.vidscreen.domain.ViewingArea;
import dev.vidscreen.protocol.message.SceneSnapshot;

public final class ScreenService {
    private final ScreenRepository repository;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final Map<UUID, ScreenState> screens = new LinkedHashMap<UUID, ScreenState>();
    private final Map<UUID, ViewingArea> areas = new LinkedHashMap<UUID, ViewingArea>();
    private long revision;

    public ScreenService(ScreenRepository repository) {
        this.repository = repository;
    }

    public void load() throws IOException {
        SceneSnapshot loaded = repository.loadScene();
        lock.writeLock().lock();
        try {
            screens.clear();
            areas.clear();
            revision = loaded.revision();
            for (ViewingArea area : loaded.areas()) {
                if (areas.containsKey(area.id()) || findAreaByNameWithoutLock(area.name(), area.dimension()) != null) {
                    throw new IOException("Duplicate viewing area in persistence: " + area.id());
                }
                areas.put(area.id(), area);
            }
            for (ScreenState screen : loaded.screens()) {
                if (screens.put(screen.definition().id(), screen) != null) {
                    throw new IOException("Duplicate screen ID in persistence: " + screen.definition().id());
                }
                revision = Math.max(revision, screen.revision());
                validateDefinitionAreaWithoutLock(screen.definition());
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

    public SceneSnapshot scene() {
        lock.readLock().lock();
        try {
            return new SceneSnapshot(revision,
                    new ArrayList<ScreenState>(screens.values()),
                    new ArrayList<ViewingArea>(areas.values()));
        } finally {
            lock.readLock().unlock();
        }
    }

    public Collection<ViewingArea> areas() {
        lock.readLock().lock();
        try {
            return Collections.unmodifiableList(new ArrayList<ViewingArea>(areas.values()));
        } finally {
            lock.readLock().unlock();
        }
    }

    public ViewingArea findArea(UUID id) {
        lock.readLock().lock();
        try {
            return areas.get(id);
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
            validateDefinitionAreaWithoutLock(definition);
            long nextRevision = nextRevision();
            ScreenState created = new ScreenState(
                    nextRevision,
                    definition,
                    null,
                    PlaybackState.stopped(nextRevision, serverTimeMillis));
            Map<UUID, ScreenState> next = copyScreens();
            next.put(definition.id(), created);
            commit(nextRevision, next, areas);
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
            commit(nextRevision, next, areas);
            return changed;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Replaces persisted geometry/name metadata while retaining the current
     * source and playback intent.  The playback timestamp is rebased at the
     * same server time so an actively playing screen does not jump backwards.
     */
    public ScreenState updateDefinition(ScreenDefinition definition, long serverTimeMillis)
            throws IOException, ScreenConflictException {
        lock.writeLock().lock();
        try {
            ScreenState current = requireScreen(definition.id());
            ScreenState sameName = findByNameWithoutLock(definition.name());
            if (sameName != null && !sameName.definition().id().equals(definition.id())) {
                throw new ScreenConflictException("A screen with that name already exists");
            }
            validateDefinitionAreaWithoutLock(definition);
            long nextRevision = nextRevision();
            PlaybackState currentPlayback = current.playback();
            PlaybackState preservedPlayback = new PlaybackState(
                    nextRevision,
                    currentPlayback.status(),
                    currentPlayback.targetPositionMillis(serverTimeMillis),
                    serverTimeMillis,
                    currentPlayback.playbackRate(),
                    currentPlayback.looping());
            ScreenState changed = new ScreenState(
                    nextRevision,
                    definition,
                    current.media(),
                    preservedPlayback);
            Map<UUID, ScreenState> next = copyScreens();
            next.put(definition.id(), changed);
            commit(nextRevision, next, areas);
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
            commit(nextRevision, next, areas);
            return changed;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** Transitions using the latest persisted position while holding the write lock. */
    public ScreenState setPlayback(UUID id, PlaybackStatus status, long serverTimeMillis) throws IOException {
        lock.writeLock().lock();
        try {
            ScreenState current = requireScreen(id);
            long positionMillis = current.playback().targetPositionMillis(serverTimeMillis);
            return setPlaybackLocked(id, status, positionMillis, serverTimeMillis);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** Seeks while preserving the latest persisted playback status. */
    public ScreenState seekPlayback(UUID id, long positionMillis, long serverTimeMillis) throws IOException {
        lock.writeLock().lock();
        try {
            ScreenState current = requireScreen(id);
            return setPlaybackLocked(id, current.playback().status(), positionMillis, serverTimeMillis);
        } finally {
            lock.writeLock().unlock();
        }
    }

    private ScreenState setPlaybackLocked(
            UUID id,
            PlaybackStatus status,
            long positionMillis,
            long serverTimeMillis) throws IOException {
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
        commit(nextRevision, next, areas);
        return changed;
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
            commit(nextRevision, next, areas);
            return changed;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** Changes only the playback rate using the latest persisted loop flag. */
    public ScreenState setRate(UUID id, double rate, long serverTimeMillis) throws IOException {
        lock.writeLock().lock();
        try {
            ScreenState current = requireScreen(id);
            return setRateAndLoopLocked(id, rate, current.playback().looping(), serverTimeMillis);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** Changes only the loop flag using the latest persisted playback rate. */
    public ScreenState setLoop(UUID id, boolean looping, long serverTimeMillis) throws IOException {
        lock.writeLock().lock();
        try {
            ScreenState current = requireScreen(id);
            return setRateAndLoopLocked(id, current.playback().playbackRate(), looping, serverTimeMillis);
        } finally {
            lock.writeLock().unlock();
        }
    }

    private ScreenState setRateAndLoopLocked(
            UUID id,
            double rate,
            boolean looping,
            long serverTimeMillis) throws IOException {
        ScreenState current = requireScreen(id);
        long nextRevision = nextRevision();
        PlaybackState playback = current.playback().withRateAndLoop(nextRevision, rate, looping, serverTimeMillis);
        ScreenState changed = current.withPlayback(nextRevision, playback);
        Map<UUID, ScreenState> next = copyScreens();
        next.put(id, changed);
        commit(nextRevision, next, areas);
        return changed;
    }

    public ScreenState delete(UUID id) throws IOException {
        lock.writeLock().lock();
        try {
            ScreenState current = requireScreen(id);
            long nextRevision = nextRevision();
            Map<UUID, ScreenState> next = copyScreens();
            next.remove(id);
            commit(nextRevision, next, areas);
            return current;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public ViewingArea createArea(ViewingArea area) throws IOException, ScreenConflictException {
        lock.writeLock().lock();
        try {
            if (areas.size() >= ViewingArea.MAX_AREAS) {
                throw new IllegalStateException("Viewing area limit reached");
            }
            if (areas.containsKey(area.id()) || findAreaByNameWithoutLock(area.name(), area.dimension()) != null) {
                throw new ScreenConflictException("A viewing area with that ID or name already exists");
            }
            long nextRevision = nextRevision();
            Map<UUID, ViewingArea> nextAreas = copyAreas();
            nextAreas.put(area.id(), area);
            commit(nextRevision, screens, nextAreas);
            return area;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public ViewingArea updateArea(ViewingArea area) throws IOException, ScreenConflictException {
        lock.writeLock().lock();
        try {
            if (!areas.containsKey(area.id())) {
                throw new IllegalArgumentException("Unknown viewing area");
            }
            ViewingArea sameName = findAreaByNameWithoutLock(area.name(), area.dimension());
            if (sameName != null && !sameName.id().equals(area.id())) {
                throw new ScreenConflictException("A viewing area with that name already exists");
            }
            validateBoundScreensForAreaWithoutLock(area);
            long nextRevision = nextRevision();
            Map<UUID, ViewingArea> nextAreas = copyAreas();
            nextAreas.put(area.id(), area);
            commit(nextRevision, screens, nextAreas);
            return area;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public ViewingArea deleteArea(UUID id) throws IOException {
        lock.writeLock().lock();
        try {
            ViewingArea current = areas.get(id);
            if (current == null) {
                throw new IllegalArgumentException("Unknown viewing area");
            }
            for (ScreenState screen : screens.values()) {
                if (id.equals(screen.definition().viewingAreaId())) {
                    throw new IllegalStateException("Viewing area is bound to a screen");
                }
            }
            long nextRevision = nextRevision();
            Map<UUID, ViewingArea> nextAreas = copyAreas();
            nextAreas.remove(id);
            commit(nextRevision, screens, nextAreas);
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

    private ViewingArea findAreaByNameWithoutLock(String name, dev.vidscreen.domain.DimensionKey dimension) {
        for (ViewingArea area : areas.values()) {
            if (area.dimension().equals(dimension) && area.name().equals(name.toLowerCase(Locale.ROOT))) {
                return area;
            }
        }
        return null;
    }

    private void validateDefinitionAreaWithoutLock(ScreenDefinition definition) {
        UUID areaId = definition.viewingAreaId();
        if (areaId == null) {
            return;
        }
        ViewingArea area = areas.get(areaId);
        if (area == null || !area.dimension().equals(definition.dimension())) {
            throw new IllegalArgumentException("Screen viewing area is missing or in another dimension");
        }
    }

    private void validateBoundScreensForAreaWithoutLock(ViewingArea area) {
        for (ScreenState screen : screens.values()) {
            if (area.id().equals(screen.definition().viewingAreaId())
                    && !area.dimension().equals(screen.definition().dimension())) {
                throw new IllegalArgumentException("Viewing area must share the bound screen dimension");
            }
        }
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

    private Map<UUID, ViewingArea> copyAreas() {
        return new LinkedHashMap<UUID, ViewingArea>(areas);
    }

    private void commit(
            long nextRevision,
            Map<UUID, ScreenState> next,
            Map<UUID, ViewingArea> nextAreas) throws IOException {
        Map<UUID, ScreenState> committedScreens = new LinkedHashMap<UUID, ScreenState>(next);
        Map<UUID, ViewingArea> committedAreas = new LinkedHashMap<UUID, ViewingArea>(nextAreas);
        repository.saveScene(new SceneSnapshot(
                nextRevision,
                new ArrayList<ScreenState>(committedScreens.values()),
                new ArrayList<ViewingArea>(committedAreas.values())));
        screens.clear();
        screens.putAll(committedScreens);
        areas.clear();
        areas.putAll(committedAreas);
        revision = nextRevision;
    }
}
