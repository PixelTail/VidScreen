package dev.vidscreen.protocol;

import java.net.URI;
import java.util.Locale;

import dev.vidscreen.domain.MediaDescriptor;

public final class CapabilityRequirements {
    private CapabilityRequirements() {
    }

    public static boolean supportsMedia(long capabilities, MediaDescriptor media) {
        if (media == null) {
            return true;
        }
        long required = requiredCapability(media);
        return required != 0 && (capabilities & required) == required;
    }

    public static long requiredCapability(MediaDescriptor media) {
        String resolver = media.resolverId();
        if ("direct".equals(resolver)) {
            try {
                String path = URI.create(media.source()).getPath().toLowerCase(Locale.ROOT);
                if (path.endsWith(".mp4")) {
                    return Capabilities.MP4;
                }
                if (path.endsWith(".m3u8")) {
                    return Capabilities.HLS;
                }
            } catch (RuntimeException ignored) {
                return 0;
            }
            return 0;
        }
        if ("bilibili".equals(resolver)) {
            return Capabilities.BILIBILI;
        }
        if ("youtube".equals(resolver)) {
            return Capabilities.YOUTUBE;
        }
        if ("twitch".equals(resolver)) {
            return Capabilities.TWITCH;
        }
        return 0;
    }
}
