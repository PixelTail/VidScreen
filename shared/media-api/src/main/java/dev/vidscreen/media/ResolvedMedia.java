package dev.vidscreen.media;

import java.net.URI;
import java.time.Instant;
import java.util.Objects;

public final class ResolvedMedia {
    private final URI streamUri;
    private final MediaKind kind;
    private final Instant expiresAt;

    public ResolvedMedia(URI streamUri, MediaKind kind, Instant expiresAt) {
        this.streamUri = Objects.requireNonNull(streamUri, "streamUri");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.expiresAt = expiresAt;
    }

    public URI streamUri() {
        return streamUri;
    }

    public MediaKind kind() {
        return kind;
    }

    public Instant expiresAt() {
        return expiresAt;
    }
}
