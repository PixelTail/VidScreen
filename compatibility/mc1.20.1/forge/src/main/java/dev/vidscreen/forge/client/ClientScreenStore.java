package dev.vidscreen.forge.client;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import dev.vidscreen.domain.ScreenState;
import dev.vidscreen.protocol.WireMessage;
import dev.vidscreen.protocol.message.PlaybackUpdate;
import dev.vidscreen.protocol.message.ScreenDelete;
import dev.vidscreen.protocol.message.ScreenSnapshot;
import dev.vidscreen.protocol.message.ScreenUpsert;

final class ClientScreenStore {
    private final Map<UUID, ScreenState> screens = new LinkedHashMap<UUID, ScreenState>();
    private long snapshotRevision;

    synchronized void accept(WireMessage message) {
        if (message instanceof ScreenSnapshot) {
            ScreenSnapshot snapshot = (ScreenSnapshot) message;
            screens.clear();
            snapshotRevision = snapshot.revision();
            for (ScreenState screen : snapshot.screens()) {
                upsert(screen);
            }
        } else if (message instanceof ScreenUpsert) {
            upsert(((ScreenUpsert) message).screen());
        } else if (message instanceof PlaybackUpdate) {
            PlaybackUpdate update = (PlaybackUpdate) message;
            ScreenState current = screens.get(update.screenId());
            if (current != null && update.playback().revision() > current.playback().revision()) {
                screens.put(update.screenId(), current.withPlayback(
                        Math.max(current.revision() + 1, update.playback().revision()), update.playback()));
            }
        } else if (message instanceof ScreenDelete) {
            ScreenDelete delete = (ScreenDelete) message;
            if (delete.revision() >= snapshotRevision) {
                screens.remove(delete.screenId());
                snapshotRevision = Math.max(snapshotRevision, delete.revision());
            }
        }
    }

    synchronized Collection<ScreenState> snapshot() {
        return Collections.unmodifiableList(new ArrayList<ScreenState>(screens.values()));
    }

    synchronized void clear() {
        screens.clear();
        snapshotRevision = 0;
    }

    private void upsert(ScreenState candidate) {
        ScreenState current = screens.get(candidate.definition().id());
        if (current == null || candidate.revision() > current.revision()) {
            screens.put(candidate.definition().id(), candidate);
            snapshotRevision = Math.max(snapshotRevision, candidate.revision());
        }
    }
}
