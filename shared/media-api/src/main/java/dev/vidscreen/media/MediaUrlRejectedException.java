package dev.vidscreen.media;

public final class MediaUrlRejectedException extends Exception {
    public MediaUrlRejectedException(String message) {
        super(message);
    }

    public MediaUrlRejectedException(String message, Throwable cause) {
        super(message, cause);
    }
}
