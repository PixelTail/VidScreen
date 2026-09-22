package dev.vidscreen.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class MediaSourcesTest {
    @Test
    void infersDirectAndProviderResolversWithoutNetworkAccess() {
        assertEquals("direct", MediaSources.parse("https://media.example/video.mp4").resolverId());
        assertEquals("direct", MediaSources.parse("https://media.example/live.m3u8").resolverId());
        assertEquals("bilibili", MediaSources.parse("https://b23.tv/example").resolverId());
        assertEquals("youtube", MediaSources.parse("https://www.youtube.com/watch?v=abc").resolverId());
    }

    @Test
    void rejectsUnsupportedResolverShapes() {
        assertThrows(IllegalArgumentException.class,
                () -> MediaSources.parse("https://media.example/video.webm"));
        assertThrows(IllegalArgumentException.class,
                () -> MediaSources.parse("https://media.example/video.mp4", "youtube"));
        assertThrows(IllegalArgumentException.class,
                () -> MediaSources.parse("http://media.example/video.mp4"));
    }
}
