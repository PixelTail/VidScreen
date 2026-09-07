package dev.vidscreen.media;

public enum PixelFormat {
    RGBA8(4),
    BGRA8(4);

    private final int bytesPerPixel;

    PixelFormat(int bytesPerPixel) {
        this.bytesPerPixel = bytesPerPixel;
    }

    public int bytesPerPixel() {
        return bytesPerPixel;
    }
}
