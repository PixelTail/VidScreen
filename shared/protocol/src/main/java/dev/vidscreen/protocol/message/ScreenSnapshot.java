package dev.vidscreen.protocol.message;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import dev.vidscreen.domain.ScreenState;
import dev.vidscreen.domain.VidScreenLimits;
import dev.vidscreen.protocol.MessageType;
import dev.vidscreen.protocol.WireMessage;

public final class ScreenSnapshot implements WireMessage {
    private final long revision;
    private final List<ScreenState> screens;

    public ScreenSnapshot(long revision, List<ScreenState> screens) {
        if (revision < 0) {
            throw new IllegalArgumentException("revision must be non-negative");
        }
        Objects.requireNonNull(screens, "screens");
        if (screens.size() > VidScreenLimits.MAX_SCREENS_PER_SNAPSHOT) {
            throw new IllegalArgumentException("Too many screens in snapshot");
        }
        this.revision = revision;
        this.screens = Collections.unmodifiableList(new ArrayList<ScreenState>(screens));
    }

    @Override
    public MessageType type() {
        return MessageType.SCREEN_SNAPSHOT;
    }

    public long revision() {
        return revision;
    }

    public List<ScreenState> screens() {
        return screens;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof ScreenSnapshot
                && revision == ((ScreenSnapshot) other).revision
                && screens.equals(((ScreenSnapshot) other).screens);
    }

    @Override
    public int hashCode() {
        return 31 * Long.hashCode(revision) + screens.hashCode();
    }
}
