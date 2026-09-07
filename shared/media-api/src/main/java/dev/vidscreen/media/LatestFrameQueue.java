package dev.vidscreen.media;

import java.util.concurrent.atomic.AtomicReference;

public final class LatestFrameQueue implements VideoFrameSink {
    private final AtomicReference<VideoFrame> latest = new AtomicReference<VideoFrame>();
    private volatile boolean closed;

    @Override
    public void submit(VideoFrame frame) {
        if (frame == null) {
            throw new NullPointerException("frame");
        }
        if (closed) {
            frame.close();
            return;
        }
        VideoFrame previous = latest.getAndSet(frame);
        if (previous != null) {
            previous.close();
        }
    }

    public VideoFrame poll() {
        return latest.getAndSet(null);
    }

    @Override
    public void close() {
        closed = true;
        VideoFrame frame = latest.getAndSet(null);
        if (frame != null) {
            frame.close();
        }
    }
}
