package dev.vidscreen.domain;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public final class MediaDescriptor {
    private static final Pattern RESOLVER_PATTERN = Pattern.compile("[a-z][a-z0-9_-]{0,63}");

    private final String resolverId;
    private final String source;

    public MediaDescriptor(String resolverId, String source) {
        Objects.requireNonNull(resolverId, "resolverId");
        Objects.requireNonNull(source, "source");

        String normalizedResolver = resolverId.toLowerCase(Locale.ROOT);
        if (!RESOLVER_PATTERN.matcher(normalizedResolver).matches()) {
            throw new IllegalArgumentException("Invalid resolver ID: " + resolverId);
        }
        if (normalizedResolver.getBytes(StandardCharsets.UTF_8).length > VidScreenLimits.MAX_RESOLVER_ID_BYTES) {
            throw new IllegalArgumentException("Resolver ID is too long");
        }
        if (source.getBytes(StandardCharsets.UTF_8).length > VidScreenLimits.MAX_MEDIA_URI_BYTES) {
            throw new IllegalArgumentException("Media source is too long");
        }

        try {
            URI uri = new URI(source);
            if (!uri.isAbsolute()) {
                throw new IllegalArgumentException("Media source must be an absolute URI");
            }
            if (!"https".equalsIgnoreCase(uri.getScheme())) {
                throw new IllegalArgumentException("Media source must use HTTPS");
            }
            if (uri.getHost() == null || uri.getHost().isEmpty()) {
                throw new IllegalArgumentException("Media source must contain a host");
            }
            if (uri.getRawUserInfo() != null) {
                throw new IllegalArgumentException("Media source cannot contain credentials");
            }
            if (uri.getRawFragment() != null) {
                throw new IllegalArgumentException("Media source cannot contain a fragment");
            }
            if (uri.getPort() != -1 && uri.getPort() != 443) {
                throw new IllegalArgumentException("Media source must use the default HTTPS port");
            }
        } catch (URISyntaxException error) {
            throw new IllegalArgumentException("Invalid media source URI", error);
        }

        this.resolverId = normalizedResolver;
        this.source = source;
    }

    public String resolverId() {
        return resolverId;
    }

    public String source() {
        return source;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof MediaDescriptor)) {
            return false;
        }
        MediaDescriptor that = (MediaDescriptor) other;
        return resolverId.equals(that.resolverId) && source.equals(that.source);
    }

    @Override
    public int hashCode() {
        return Objects.hash(resolverId, source);
    }
}
