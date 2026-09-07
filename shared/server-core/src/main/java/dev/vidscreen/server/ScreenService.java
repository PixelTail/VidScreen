package dev.vidscreen.server;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import dev.vidscreen.domain.MediaDescriptor;
import dev.vidscreen.domain.PlaybackState;
import dev.vidscreen.domain.PlaybackStatus;
import dev.vidscreen.domain.ScreenDefinition;
import dev.vidscreen.domain.ScreenState;

/**
 * Thread-safe authoritative screen service shared by loader and plugin adapters.
 * Mutations are bounded by the domain constructors and persisted before publication.
 */
public final class ScreenService {
    private final ScreenRepository repository;
    private final List<ScreenState> screens = new ArrayList<ScreenState>();
    private long revision;

    public ScreenService(ScreenRepository repository) {
        this.repository = repository;
    }

    public synchronized void load() throws IOException {
        screens.clear();
        screens.addAll(repository.load());
        revision = 0;
        for (ScreenState screen : screens) {
            revision = Math.max(revision, screen.revision());
        }
    }

    public synchronized long revision() {
        return revision;
    }

    public synchronized List<ScreenState> snapshot() {
        List<ScreenState> copy = new ArrayList<ScreenState>(screens);
        copy.sort(Comparator.comparing(screen -> screen.definition().id().toString()));
        return Collections.unmodifiableList(copy);
    }

    public synchronized ScreenState findByName(String name) {
        for (ScreenState screen : screens) {
            if (screen.definition().name().equals(name)) {
                return screen;
            }
        }
        return null;
    }

    public synchronized ScreenState create(ScreenDefinition definition, long serverTimeMillis) throws IOException {
        if (findByName(definition.name()) != null) {
            throw new IllegalArgumentException("A screen with that name already exists");
        }
        long nextRevision = nextRevision();
        ScreenState created = new ScreenState(
                nextRevision, definition, null, PlaybackState.stopped(0, serverTimeMillis));
        screens.add(created);
        persist();
        return created;
    }

    public synchronized ScreenState delete(UUID screenId) throws IOException {
        for (int index = 0; index < screens.size(); index++) {
            ScreenState screen = screens.get(index);
            if (screen.definition().id().equals(screenId)) {
                screens.remove(index);
                revision = nextRevision();
                persist();
                return screen;
            }
        }
        throw new IllegalArgumentException("Unknown screen: " + screenId);
    }

    public synchronized ScreenState setMedia(UUID screenId, MediaDescriptor media, long serverTimeMillis)
            throws IOException {
        ScreenState current = require(screenId);
        long nextRevision = nextRevision();
        PlaybackState playback = current.playback().transition(
                current.playback().revision() + 1,
                PlaybackStatus.STOPPED,
                0,
                serverTimeMillis);
        ScreenState changed = current.withMedia(nextRevision, media, playback);
        replace(current, changed);
        persist();
        return changed;
    }

    public synchronized ScreenState setPlayback(
            UUID screenId, PlaybackStatus status, long positionMillis, long serverTimeMillis) throws IOException {
        ScreenState current = require(screenId);
        long nextRevision = nextRevision();
        PlaybackState playback = current.playback().transition(
                current.playback().revision() + 1,
                status,
                positionMillis,
                serverTimeMillis);
        ScreenState changed = current.withPlayback(nextRevision, playback);
        replace(current, changed);
        persist();
        return changed;
    }

    public synchronized ScreenState setRateAndLoop(
            UUID screenId, double rate, boolean looping, long serverTimeMillis) throws IOException {
        ScreenState current = require(screenId);
        long nextRevision = nextRevision();
        PlaybackState playback = current.playback().withRateAndLoop(
                current.playback().revision() + 1, rate, looping, serverTimeMillis);
        ScreenState changed = current.withPlayback(nextRevision, playback);
        replace(current, changed);
        persist();
        return changed;
    }

    private ScreenState require(UUID screenId) {
        for (ScreenState screen : screens) {
            if (screen.definition().id().equals(screenId)) {
                return screen;
            }
        }
        throw new IllegalArgumentException("Unknown screen: " + screenId);
    }

    private void replace(ScreenState previous, ScreenState changed) {
        int index = screens.indexOf(previous);
        if (index < 0) {
            throw new IllegalStateException("Screen changed while mutating");
        }
        screens.set(index, changed);
    }

    private long nextRevision() {
        if (revision == Long.MAX_VALUE) {
            throw new IllegalStateException("VidScreen revision exhausted");
        }
        return ++revision;
    }

    private void persist() throws IOException {
        repository.save(revision, screens);
    }
}
