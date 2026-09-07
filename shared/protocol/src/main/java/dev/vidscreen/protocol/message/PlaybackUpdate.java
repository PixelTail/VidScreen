package dev.vidscreen.protocol.message;

import java.util.Objects;
import java.util.UUID;

import dev.vidscreen.domain.PlaybackState;
import dev.vidscreen.protocol.MessageType;
import dev.vidscreen.protocol.WireMessage;

public final class PlaybackUpdate implements WireMessage {
    private final UUID screenId;
    private final PlaybackState playback;

    public PlaybackUpdate(UUID screenId, PlaybackState playback) {
        this.screenId = Objects.requireNonNull(screenId, "screenId");
        this.playback = Objects.requireNonNull(playback, "playback");
    }

    @Override
    public MessageType type() {
        return MessageType.PLAYBACK_UPDATE;
    }

    public UUID screenId() {
        return screenId;
    }

    public PlaybackState playback() {
        return playback;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof PlaybackUpdate
                && screenId.equals(((PlaybackUpdate) other).screenId)
                && playback.equals(((PlaybackUpdate) other).playback);
    }

    @Override
    public int hashCode() {
        return 31 * screenId.hashCode() + playback.hashCode();
    }
}
