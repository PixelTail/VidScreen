package dev.vidscreen.client;

import java.util.UUID;

public final class PlaybackFailure {
    private final UUID screenId;
    private final String stage;
    private final Throwable cause;

    public PlaybackFailure(UUID screenId, String stage, Throwable cause) {
        this.screenId = screenId;
        this.stage = stage;
        this.cause = cause;
    }

    public UUID screenId() {
        return screenId;
    }

    public String stage() {
        return stage;
    }

    public Throwable cause() {
        return cause;
    }
}
