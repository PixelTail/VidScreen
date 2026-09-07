package dev.vidscreen.media;

import java.nio.ByteBuffer;
import java.util.Objects;

public final class VideoFrame implements AutoCloseable {
    private final int width;
    private final int height;
    private final int rowStride;
    private final PixelFormat format;
    private final long presentationTimeMicros;
    private final ByteBuffer pixels;
    private final Runnable releaser;
    private boolean closed;

    public VideoFrame(
            int width,
            int height,
            int rowStride,
            PixelFormat format,
            long presentationTimeMicros,
            ByteBuffer pixels,
            Runnable releaser) {
        if (width < 1 || height < 1 || width > 16_384 || height > 16_384) {
            throw new IllegalArgumentException("Frame dimensions are outside supported bounds");
        }
        this.format = Objects.requireNonNull(format, "format");
        long minimumStride = (long) width * format.bytesPerPixel();
        long requiredBytes = (long) rowStride * height;
        if (rowStride < minimumStride || requiredBytes > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Invalid frame stride");
        }
        this.pixels = Objects.requireNonNull(pixels, "pixels").asReadOnlyBuffer();
        if (this.pixels.remaining() < requiredBytes) {
            throw new IllegalArgumentException("Pixel buffer is smaller than the declared frame");
        }
        this.width = width;
        this.height = height;
        this.rowStride = rowStride;
        this.presentationTimeMicros = presentationTimeMicros;
        this.releaser = Objects.requireNonNull(releaser, "releaser");
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int rowStride() {
        return rowStride;
    }

    public PixelFormat format() {
        return format;
    }

    public long presentationTimeMicros() {
        return presentationTimeMicros;
    }

    public ByteBuffer pixels() {
        if (closed) {
            throw new IllegalStateException("Frame is closed");
        }
        return pixels.asReadOnlyBuffer();
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            releaser.run();
        }
    }
}
