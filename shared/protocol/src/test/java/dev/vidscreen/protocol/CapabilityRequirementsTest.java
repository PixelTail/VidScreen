package dev.vidscreen.protocol;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import dev.vidscreen.domain.MediaDescriptor;

class CapabilityRequirementsTest {
    @Test
    void mapsDirectFormatsToTheirOwnCapabilities() {
        MediaDescriptor mp4 = new MediaDescriptor("direct", "https://media.example/video.mp4?token=opaque");
        MediaDescriptor hls = new MediaDescriptor("direct", "https://media.example/live.m3u8?token=opaque");

        assertTrue(CapabilityRequirements.supportsMedia(Capabilities.MP4, mp4));
        assertFalse(CapabilityRequirements.supportsMedia(Capabilities.HLS, mp4));
        assertTrue(CapabilityRequirements.supportsMedia(Capabilities.HLS, hls));
        assertFalse(CapabilityRequirements.supportsMedia(Capabilities.MP4, hls));
    }

    @Test
    void rejectsUnknownResolversAndRequiresProviderBit() {
        MediaDescriptor youtube = new MediaDescriptor("youtube", "https://www.youtube.com/watch?v=test");
        MediaDescriptor unknown = new MediaDescriptor("future", "https://media.example/watch/test");

        assertTrue(CapabilityRequirements.supportsMedia(Capabilities.YOUTUBE, youtube));
        assertFalse(CapabilityRequirements.supportsMedia(Capabilities.MP4 | Capabilities.HLS, youtube));
        assertFalse(CapabilityRequirements.supportsMedia(Long.MAX_VALUE, unknown));
    }

    @Test
    void allowsScreensWithoutMedia() {
        assertTrue(CapabilityRequirements.supportsMedia(0, null));
    }
}
