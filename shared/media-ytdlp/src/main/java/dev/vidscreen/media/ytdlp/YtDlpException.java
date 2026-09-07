package dev.vidscreen.media.ytdlp;

public final class YtDlpException extends RuntimeException {
    public YtDlpException(String message) {
        super(message);
    }

    public YtDlpException(String message, Throwable cause) {
        super(message, cause);
    }
}
