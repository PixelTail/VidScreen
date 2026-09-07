package dev.vidscreen.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class LatestFrameQueueTest {
    @Test
    void keepsLatestAndReleasesDroppedFrames() {
        LatestFrameQueue queue = new LatestFrameQueue();
        AtomicInteger releases = new AtomicInteger();
        VideoFrame first = frame(1, releases);
        VideoFrame second = frame(2, releases);

        queue.submit(first);
        queue.submit(second);

        assertEquals(1, releases.get());
        VideoFrame polled = queue.poll();
        assertEquals(2, polled.presentationTimeMicros());
        polled.close();
        assertEquals(2, releases.get());
        assertNull(queue.poll());
    }

    private static VideoFrame frame(long pts, AtomicInteger releases) {
        return new VideoFrame(1, 1, 4, PixelFormat.RGBA8, pts,
                ByteBuffer.wrap(new byte[] {0, 0, 0, (byte) 255}), releases::incrementAndGet);
    }
}
