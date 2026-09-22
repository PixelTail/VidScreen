package dev.vidscreen.domain;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Pure URL/resolver validation shared by clients and server-side editors. */
public final class MediaSources {
    private MediaSources() {
    }

    /** Infers a supported resolver from an HTTPS source and validates its shape. */
    public static MediaDescriptor parse(String source) {
        return parse(source, null);
    }

    /** Validates an HTTPS source against the explicit resolver, or infers one when null. */
    public static MediaDescriptor parse(String source, String resolver) {
        if (source == null) {
            throw new IllegalArgumentException("Media source is required");
        }
        URI uri;
        try {
            uri = new URI(source);
        } catch (Exception error) {
            throw new IllegalArgumentException("Media source is not a valid URI");
        }
        validateUri(uri);
        String selectedResolver = resolver == null || resolver.trim().isEmpty()
                ? inferResolver(uri)
                : resolver.toLowerCase(Locale.ROOT);
        validateResolverSource(selectedResolver, uri);
        return new MediaDescriptor(selectedResolver, uri.toASCIIString());
    }

    /** Validates a descriptor received through the bounded editor protocol. */
    public static void validate(MediaDescriptor media) {
        if (media == null) {
            throw new IllegalArgumentException("Media source is required");
        }
        URI uri;
        try {
            uri = new URI(media.source());
        } catch (Exception error) {
            throw new IllegalArgumentException("Media source is not a valid URI");
        }
        validateUri(uri);
        validateResolverSource(media.resolverId().toLowerCase(Locale.ROOT), uri);
    }

    public static String inferResolver(String source) {
        if (source == null) {
            throw new IllegalArgumentException("Media source is required");
        }
        try {
            URI uri = new URI(source);
            validateUri(uri);
            return inferResolver(uri);
        } catch (IllegalArgumentException error) {
            throw error;
        } catch (Exception error) {
            throw new IllegalArgumentException("Media source is not a valid URI");
        }
    }

    private static String inferResolver(URI source) {
        String host = source.getHost().toLowerCase(Locale.ROOT);
        if (providerHost(host, "bilibili.com") || providerHost(host, "b23.tv")) {
            return "bilibili";
        }
        if (providerHost(host, "youtube.com") || providerHost(host, "youtu.be")) {
            return "youtube";
        }
        if (providerHost(host, "twitch.tv")) {
            return "twitch";
        }
        return "direct";
    }

    private static void validateUri(URI source) {
        if (!source.isAbsolute() || !"https".equalsIgnoreCase(source.getScheme()) || source.getHost() == null) {
            throw new IllegalArgumentException("Media source must be an absolute HTTPS URL");
        }
        if (source.getRawUserInfo() != null || source.getRawFragment() != null) {
            throw new IllegalArgumentException("Media source cannot contain credentials or a fragment");
        }
        if (source.getPort() != -1 && source.getPort() != 443) {
            throw new IllegalArgumentException("Media source must use the default HTTPS port");
        }
        if (source.toASCIIString().getBytes(StandardCharsets.UTF_8).length > VidScreenLimits.MAX_MEDIA_URI_BYTES) {
            throw new IllegalArgumentException("Media source is too long");
        }
    }

    private static void validateResolverSource(String resolver, URI source) {
        String host = source.getHost().toLowerCase(Locale.ROOT);
        String path = source.getPath().toLowerCase(Locale.ROOT);
        boolean valid;
        switch (resolver) {
            case "direct":
                valid = path.endsWith(".mp4") || path.endsWith(".m3u8");
                break;
            case "bilibili":
                valid = providerHost(host, "bilibili.com") || providerHost(host, "b23.tv");
                break;
            case "youtube":
                valid = providerHost(host, "youtube.com") || providerHost(host, "youtu.be");
                break;
            case "twitch":
                valid = providerHost(host, "twitch.tv");
                break;
            default:
                throw new IllegalArgumentException("Unknown media resolver");
        }
        if (!valid) {
            throw new IllegalArgumentException("Media URL does not match its resolver");
        }
    }

    private static boolean providerHost(String host, String suffix) {
        return host.equals(suffix) || host.endsWith("." + suffix);
    }
}
