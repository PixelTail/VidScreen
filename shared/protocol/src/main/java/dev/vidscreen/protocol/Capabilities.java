package dev.vidscreen.protocol;

public final class Capabilities {
    public static final long MP4 = 1L;
    public static final long HLS = 1L << 1;
    public static final long BILIBILI = 1L << 2;
    public static final long YOUTUBE = 1L << 3;
    public static final long TWITCH = 1L << 4;
    public static final long LIVE_STREAMS = 1L << 5;
    public static final long SPATIAL_AUDIO = 1L << 6;

    private Capabilities() {
    }
}
