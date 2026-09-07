package dev.vidscreen.protocol.message;

import java.util.Objects;
import java.util.UUID;

import dev.vidscreen.protocol.MessageType;
import dev.vidscreen.protocol.WireMessage;

public final class ScreenDelete implements WireMessage {
    private final UUID screenId;
    private final long revision;

    public ScreenDelete(UUID screenId, long revision) {
        if (revision < 0) {
            throw new IllegalArgumentException("revision must be non-negative");
        }
        this.screenId = Objects.requireNonNull(screenId, "screenId");
        this.revision = revision;
    }

    @Override
    public MessageType type() {
        return MessageType.SCREEN_DELETE;
    }

    public UUID screenId() {
        return screenId;
    }

    public long revision() {
        return revision;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof ScreenDelete
                && screenId.equals(((ScreenDelete) other).screenId)
                && revision == ((ScreenDelete) other).revision;
    }

    @Override
    public int hashCode() {
        return 31 * screenId.hashCode() + Long.hashCode(revision);
    }
}
