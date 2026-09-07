package dev.vidscreen.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class MediaDescriptorTest {
    @Test
    void acceptsPublicShapeHttpsSource() {
        MediaDescriptor media = new MediaDescriptor("YouTube", "https://www.youtube.com/watch?v=test");

        assertEquals("youtube", media.resolverId());
        assertEquals("https://www.youtube.com/watch?v=test", media.source());
    }

    @Test
    void rejectsUnsafeUrlComponents() {
        assertThrows(IllegalArgumentException.class,
                () -> new MediaDescriptor("direct", "http://media.example/video.mp4"));
        assertThrows(IllegalArgumentException.class,
                () -> new MediaDescriptor("direct", "https://user:secret@media.example/video.mp4"));
        assertThrows(IllegalArgumentException.class,
                () -> new MediaDescriptor("direct", "https://media.example/video.mp4#fragment"));
        assertThrows(IllegalArgumentException.class,
                () -> new MediaDescriptor("direct", "https://media.example:8443/video.mp4"));
    }
}
