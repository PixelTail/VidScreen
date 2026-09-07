package dev.vidscreen.domain;

public final class VidScreenLimits {
    public static final int MAX_SCREEN_NAME_BYTES = 64;
    public static final int MAX_DIMENSION_KEY_BYTES = 128;
    public static final int MAX_MEDIA_URI_BYTES = 4_096;
    public static final int MAX_RESOLVER_ID_BYTES = 64;
    public static final int MAX_OPERATION_MESSAGE_BYTES = 1_024;
    public static final int MAX_SCREENS_PER_SNAPSHOT = 2_048;
    public static final int MAX_SCREEN_WIDTH_BLOCKS = 256;
    public static final int MAX_SCREEN_HEIGHT_BLOCKS = 256;
    public static final long MAX_SCREEN_AREA_BLOCKS = 65_536L;
    public static final int MAX_WIRE_PAYLOAD_BYTES = 8 * 1024 * 1024;

    private VidScreenLimits() {
    }
}
