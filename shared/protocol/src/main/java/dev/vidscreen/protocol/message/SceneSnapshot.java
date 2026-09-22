package dev.vidscreen.protocol.message;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import dev.vidscreen.domain.ScreenState;
import dev.vidscreen.domain.ViewingArea;
import dev.vidscreen.protocol.MessageType;
import dev.vidscreen.protocol.WireMessage;

/** Editor-capable scene snapshot; legacy screen message encodings remain unchanged. */
public final class SceneSnapshot implements WireMessage {
    private final ScreenSnapshot screens;
    private final List<ViewingArea> areas;
    public SceneSnapshot(long revision, List<ScreenState> screens, List<ViewingArea> areas) {
        this.screens = new ScreenSnapshot(revision, screens);
        Objects.requireNonNull(areas, "areas");
        if (areas.size() > ViewingArea.MAX_AREAS) { throw new IllegalArgumentException("Too many viewing areas"); }
        this.areas = Collections.unmodifiableList(new ArrayList<ViewingArea>(areas));
    }
    public long revision() { return screens.revision(); }
    public List<ScreenState> screens() { return screens.screens(); }
    public List<ViewingArea> areas() { return areas; }
    @Override public MessageType type() { return MessageType.SCENE_SNAPSHOT; }
    @Override public boolean equals(Object other) {
        return other instanceof SceneSnapshot && screens.equals(((SceneSnapshot) other).screens) && areas.equals(((SceneSnapshot) other).areas);
    }
    @Override public int hashCode() { return Objects.hash(screens, areas); }
}
