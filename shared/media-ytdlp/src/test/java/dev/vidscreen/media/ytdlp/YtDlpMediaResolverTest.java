package dev.vidscreen.media.ytdlp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

import dev.vidscreen.media.MediaKind;
import dev.vidscreen.media.MediaUrlPolicy;

class YtDlpMediaResolverTest {
    @Test
    void matchesOnlyProviderHostBoundaries() {
        YtDlpMediaResolver resolver = YtDlpMediaResolver.youtube(
                Paths.get("yt-dlp"), MediaUrlPolicy.strictPublicHttps());

        assertTrue(resolver.supports(URI.create("https://www.youtube.com/watch?v=test")));
        assertTrue(resolver.supports(URI.create("https://youtu.be/test")));
        assertFalse(resolver.supports(URI.create("https://youtube.com.example.test/watch?v=test")));
        assertFalse(resolver.supports(URI.create("https://example.test/watch?v=test")));
    }

    @Test
    void parsesLiveAndHlsMetadata() {
        YtDlpMediaResolver.Extraction live = YtDlpMediaResolver.parse(
                "m3u8_native\tTrue\thttps://media.example/live.m3u8\n".getBytes(StandardCharsets.UTF_8));
        YtDlpMediaResolver.Extraction vod = YtDlpMediaResolver.parse(
                "m3u8_native\tFalse\thttps://media.example/video.m3u8\n".getBytes(StandardCharsets.UTF_8));

        assertEquals(MediaKind.LIVE, YtDlpMediaResolver.mediaKind(live));
        assertEquals(MediaKind.HLS, YtDlpMediaResolver.mediaKind(vod));
    }

    @Test
    void rejectsMalformedExtractorOutput() {
        assertThrows(YtDlpException.class, () -> YtDlpMediaResolver.parse(
                "https://media.example/video.mp4\n".getBytes(StandardCharsets.UTF_8)));
    }
}
