package dev.vidscreen.protocol;

import java.net.URI;
import java.util.Locale;

import dev.vidscreen.domain.MediaDescriptor;
import dev.vidscreen.domain.PlaybackState;
import dev.vidscreen.domain.PlaybackStatus;
import dev.vidscreen.domain.ScreenState;

public final class CapabilityRequirements {
    private CapabilityRequirements() {
    }

    /** Keep the physical screen visible without disclosing an unsupported media source. */
    public static ScreenState visibleState(long capabilities, ScreenState screen) {
        if (supportsMedia(capabilities, screen.media())) {
            return screen;
        }
        PlaybackState state = screen.playback();
        return new ScreenState(screen.revision(), screen.definition(), null,
                new PlaybackState(state.revision(), PlaybackStatus.FAILED, 0,
                        state.effectiveServerTimeMillis(), 1.0, false));
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
