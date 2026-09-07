package dev.vidscreen.domain;

import java.util.Objects;

public final class ScreenState {
    private final long revision;
    private final ScreenDefinition definition;
    private final MediaDescriptor media;
    private final PlaybackState playback;

    public ScreenState(long revision, ScreenDefinition definition, MediaDescriptor media, PlaybackState playback) {
        if (revision < 0) {
            throw new IllegalArgumentException("revision must be non-negative");
        }
        this.revision = revision;
        this.definition = Objects.requireNonNull(definition, "definition");
        this.media = media;
        this.playback = Objects.requireNonNull(playback, "playback");
    }

    public long revision() {
        return revision;
    }

    public ScreenDefinition definition() {
        return definition;
    }

    public MediaDescriptor media() {
        return media;
    }

    public PlaybackState playback() {
        return playback;
    }

    public ScreenState withMedia(long nextRevision, MediaDescriptor nextMedia, PlaybackState nextPlayback) {
        requireNewer(nextRevision);
        return new ScreenState(nextRevision, definition, nextMedia, nextPlayback);
    }

    public ScreenState withPlayback(long nextRevision, PlaybackState nextPlayback) {
        requireNewer(nextRevision);
        return new ScreenState(nextRevision, definition, media, nextPlayback);
    }

    private void requireNewer(long nextRevision) {
        if (nextRevision <= revision) {
            throw new IllegalArgumentException("nextRevision must be greater than the current revision");
        }
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ScreenState)) {
            return false;
        }
        ScreenState that = (ScreenState) other;
        return revision == that.revision
                && definition.equals(that.definition)
                && Objects.equals(media, that.media)
                && playback.equals(that.playback);
    }

    @Override
    public int hashCode() {
        return Objects.hash(revision, definition, media, playback);
    }
}
