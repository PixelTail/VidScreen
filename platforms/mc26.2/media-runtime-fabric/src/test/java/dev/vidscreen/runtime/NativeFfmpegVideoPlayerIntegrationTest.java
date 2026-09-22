package dev.vidscreen.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import dev.vidscreen.media.MediaKind;
import dev.vidscreen.media.MediaPlayerState;
import dev.vidscreen.media.MediaUrlPolicy;
import dev.vidscreen.media.PixelFormat;
import dev.vidscreen.media.ResolvedMedia;
import dev.vidscreen.media.VideoFrame;
import dev.vidscreen.media.VideoFrameSink;
import dev.vidscreen.media.ffmpeg.nativeapi.NativeFfmpegRuntime;
import dev.vidscreen.media.ffmpeg.nativeapi.NativeFfmpegVideoPlayer;

class NativeFfmpegVideoPlayerIntegrationTest {
    private static final String ENABLE_PROPERTY = "vidscreen.runNativeMediaIntegration";
    // Official archived Blender Developer Wiki file page:
    // https://archive.blender.org/wiki/2024/wiki/File%3ATestx2.mp4.html
    private static final String OFFICIAL_MP4 =
            "https://archive.blender.org/wiki/2024/w/images/c/c2/Testx2.mp4";
    private static final int OUTPUT_WIDTH = 640;
    private static final int OUTPUT_HEIGHT = 360;
    private static final long OPEN_TIMEOUT_SECONDS = 30;
    private static final long FRAME_TIMEOUT_SECONDS = 30;
    private static final long DECODER_EXIT_TIMEOUT_SECONDS = 17;

    @Test
    void opensOfficialMp4ProducesFramesSupportsPauseSeekAndCloses() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean(ENABLE_PROPERTY),
                "Set -D" + ENABLE_PROPERTY + "=true to run the network media integration test");
        assertTrue(NativeFfmpegRuntime.isAvailable(),
                "The bundled native media runtime is unavailable on this host");

        URI source = URI.create(OFFICIAL_MP4);
        URI validatedSource = MediaUrlPolicy.strictPublicHttps().validate(source);
        ResolvedMedia media = new ResolvedMedia(validatedSource, MediaKind.MP4, null);
        NativeFfmpegVideoPlayer player = new NativeFfmpegVideoPlayer(OUTPUT_WIDTH, OUTPUT_HEIGHT);
        CollectingSink sink = new CollectingSink();
        int decoderThreadsBefore = decoderThreadCount();

        try {
            player.open(media, sink).toCompletableFuture().get(OPEN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            assertTrue(player.durationMillis() > 0, "The finite fixture must expose a duration");

            player.play();
            long previousPresentationTime = -1;
            for (int index = 0; index < 3; index++) {
                VideoFrame frame = requireFrame(sink);
                try {
                    assertEquals(OUTPUT_WIDTH, frame.width());
                    assertEquals(OUTPUT_HEIGHT, frame.height());
                    assertEquals(PixelFormat.RGBA8, frame.format());
                    assertTrue(frame.pixels().remaining() >= OUTPUT_WIDTH * OUTPUT_HEIGHT * 4);
                    assertTrue(frame.presentationTimeMicros() > previousPresentationTime,
                            "decoded frame timestamps must advance");
                    previousPresentationTime = frame.presentationTimeMicros();
                } finally {
                    frame.close();
                }
            }

            player.pause();
            assertEquals(MediaPlayerState.PAUSED, player.state());
            sink.discardQueuedFrames();
            long seekMillis = Math.min(500L, Math.max(1L, player.durationMillis() / 3));
            player.seek(Duration.ofMillis(seekMillis));
            player.requestFrame();
            VideoFrame seekFrame = requireFrame(sink);
            try {
                assertTrue(seekFrame.presentationTimeMicros() >= 0);
            } finally {
                seekFrame.close();
            }

            player.play();
            assertEquals(MediaPlayerState.PLAYING, player.state());
        } finally {
            player.close();
            sink.close();
            assertTrue(awaitDecoderThreadsAtMost(decoderThreadsBefore, DECODER_EXIT_TIMEOUT_SECONDS),
                    "native decoder thread did not exit within the configured I/O timeout margin");
            assertEquals(MediaPlayerState.CLOSED, player.state());
        }
    }

    private static VideoFrame requireFrame(CollectingSink sink) throws InterruptedException {
        VideoFrame frame = sink.frames.poll(FRAME_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertNotNull(frame, "native decoder did not submit a frame in time");
        return frame;
    }

    private static boolean awaitDecoderThreadsAtMost(int expectedMaximum, long timeoutSeconds)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
        while (System.nanoTime() < deadline) {
            if (decoderThreadCount() <= expectedMaximum) {
                return true;
            }
            Thread.sleep(50L);
        }
        return decoderThreadCount() <= expectedMaximum;
    }

    private static int decoderThreadCount() {
        int count = 0;
        for (Thread thread : Thread.getAllStackTraces().keySet()) {
            if ("vidscreen-native-ffmpeg".equals(thread.getName()) && thread.isAlive()) {
                count++;
            }
        }
        return count;
    }

    private static final class CollectingSink implements VideoFrameSink {
        private final BlockingQueue<VideoFrame> frames = new ArrayBlockingQueue<VideoFrame>(8);
        private volatile boolean closed;

        @Override
        public synchronized void submit(VideoFrame frame) {
            if (closed || !frames.offer(frame)) {
                frame.close();
            }
        }

        @Override
        public synchronized void close() {
            closed = true;
            discardQueuedFrames();
        }

        synchronized void discardQueuedFrames() {
            VideoFrame frame;
            while ((frame = frames.poll()) != null) {
                frame.close();
            }
        }
    }
}
