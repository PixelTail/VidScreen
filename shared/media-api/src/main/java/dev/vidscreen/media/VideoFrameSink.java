package dev.vidscreen.media;

public interface VideoFrameSink extends AutoCloseable {
    void submit(VideoFrame frame);

    @Override
    void close();
}
