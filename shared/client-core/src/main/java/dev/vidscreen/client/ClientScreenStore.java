package dev.vidscreen.client;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import dev.vidscreen.domain.ScreenState;
import dev.vidscreen.domain.VidScreenLimits;
import dev.vidscreen.domain.ViewingArea;
import dev.vidscreen.domain.BlockPoint;
import java.util.List;
import dev.vidscreen.protocol.WireMessage;
import dev.vidscreen.protocol.message.PlaybackUpdate;
import dev.vidscreen.protocol.message.ScreenDelete;
import dev.vidscreen.protocol.message.ScreenSnapshot;
import dev.vidscreen.protocol.message.ScreenUpsert;
import dev.vidscreen.protocol.message.SceneSnapshot;
import dev.vidscreen.protocol.message.SceneUpsert;

/** Client state shared by all loaders; messages arrive in Minecraft connection order. */
public final class ClientScreenStore {
    private final Map<UUID, ScreenState> screens = new LinkedHashMap<UUID, ScreenState>();
    private long snapshotRevision;
    private ScreenState preview;
    private final Map<UUID, ViewingArea> areas = new LinkedHashMap<UUID, ViewingArea>();
    private ViewingArea areaPreview;
    private List<BlockPoint> selectionPoints = Collections.emptyList();
    private String previewDimension;

    public synchronized void accept(WireMessage message) {
        if (message instanceof SceneSnapshot) {
            SceneSnapshot scene = (SceneSnapshot) message;
            if (scene.revision() < snapshotRevision) { return; }
            areas.clear();
            for (ViewingArea area : scene.areas()) { areas.put(area.id(), area); }
            accept(new ScreenSnapshot(scene.revision(), scene.screens()));
        } else if (message instanceof SceneUpsert) {
            upsert(((SceneUpsert) message).screen());
        } else if (message instanceof ScreenSnapshot) {
            ScreenSnapshot snapshot = (ScreenSnapshot) message;
            if (snapshot.revision() < snapshotRevision) {
                return;
            }
            screens.clear();
            snapshotRevision = snapshot.revision();
            for (ScreenState screen : snapshot.screens()) {
                upsert(screen);
            }
        } else if (message instanceof ScreenUpsert) {
            // A snapshot starts with an empty reset then sends individual screen
            // revisions, which can be older than the snapshot's global revision.
            upsert(((ScreenUpsert) message).screen());
        } else if (message instanceof PlaybackUpdate) {
            PlaybackUpdate update = (PlaybackUpdate) message;
            ScreenState current = screens.get(update.screenId());
            if (current != null && update.playback().revision() > current.playback().revision()) {
                screens.put(update.screenId(), new ScreenState(
                        Math.max(current.revision(), update.playback().revision()),
                        current.definition(), current.media(), update.playback()));
            }
        } else if (message instanceof ScreenDelete) {
            ScreenDelete delete = (ScreenDelete) message;
            ScreenState current = screens.get(delete.screenId());
            if (current != null && delete.revision() >= current.revision()) {
                screens.remove(delete.screenId());
            }
        }
    }

    public synchronized Collection<ScreenState> snapshot() {
        return Collections.unmodifiableList(new ArrayList<ScreenState>(screens.values()));
    }

    public synchronized Collection<ScreenState> renderSnapshot() {
        ArrayList<ScreenState> visible = new ArrayList<ScreenState>(screens.values());
        if (preview != null) {
            java.util.Iterator<ScreenState> iterator = visible.iterator();
            while (iterator.hasNext()) {
                if (iterator.next().definition().id().equals(preview.definition().id())) { iterator.remove(); }
            }
            visible.add(preview);
        }
        return Collections.unmodifiableList(visible);
    }

    public synchronized void setPreview(ScreenState preview) { this.preview = preview; }

    public synchronized Collection<ViewingArea> areas() {
        return Collections.unmodifiableList(new ArrayList<ViewingArea>(areas.values()));
    }

    public synchronized Collection<ScreenState> playbackSnapshot(String dimension, double x, double y, double z) {
        ArrayList<ScreenState> visible = new ArrayList<ScreenState>();
        for (ScreenState screen : screens.values()) {
            UUID areaId = screen.definition().viewingAreaId();
            ViewingArea area = areas.get(areaId);
            if (areaId == null || area != null && area.contains(dimension, x, y, z)) { visible.add(screen); }
        }
        return Collections.unmodifiableList(visible);
    }

    public synchronized void setAreaPreview(ViewingArea area) { areaPreview = area; }
    public synchronized ViewingArea areaPreview() { return areaPreview; }
    public synchronized void setSelectionPoints(List<BlockPoint> points) {
        selectionPoints = Collections.unmodifiableList(new ArrayList<BlockPoint>(points));
    }
    public synchronized List<BlockPoint> selectionPoints() { return selectionPoints; }
    public synchronized void setPreviewDimension(String dimension) { previewDimension = dimension; }
    public synchronized String previewDimension() { return previewDimension; }

    public synchronized void clear() {
        screens.clear();
        snapshotRevision = 0;
        preview = null;
        areas.clear(); areaPreview = null; selectionPoints = Collections.emptyList(); previewDimension = null;
    }

    private void upsert(ScreenState candidate) {
        UUID id = candidate.definition().id();
        ScreenState current = screens.get(id);
        if (current == null && screens.size() >= VidScreenLimits.MAX_SCREENS_PER_SNAPSHOT) {
            throw new IllegalStateException("Too many client screens");
        }
        if (current == null || candidate.revision() > current.revision()) {
            screens.put(id, candidate);
        }
    }
}
