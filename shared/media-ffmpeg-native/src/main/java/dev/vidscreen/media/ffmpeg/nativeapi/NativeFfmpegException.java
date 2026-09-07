package dev.vidscreen.media.ffmpeg.nativeapi;

public final class NativeFfmpegException extends RuntimeException {
    public NativeFfmpegException(String message) {
        super(message);
    }

    public NativeFfmpegException(String message, Throwable cause) {
        super(message, cause);
    }
}
