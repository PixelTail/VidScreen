package dev.vidscreen.media;

import java.net.URI;
import java.util.Objects;

public final class MediaRequest {
    private final URI source;
    private final int preferredWidth;
    private final int preferredHeight;

    public MediaRequest(URI source, int preferredWidth, int preferredHeight) {
        this.source = Objects.requireNonNull(source, "source");
        if (preferredWidth < 1 || preferredWidth > 16_384 || preferredHeight < 1 || preferredHeight > 16_384) {
            throw new IllegalArgumentException("Preferred dimensions must be between 1 and 16384");
        }
        this.preferredWidth = preferredWidth;
        this.preferredHeight = preferredHeight;
    }

    public URI source() {
        return source;
    }

    public int preferredWidth() {
        return preferredWidth;
    }

    public int preferredHeight() {
        return preferredHeight;
    }
}
