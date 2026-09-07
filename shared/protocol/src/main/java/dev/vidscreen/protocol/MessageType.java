package dev.vidscreen.protocol;

public enum MessageType {
    CLIENT_HELLO(1),
    SERVER_HELLO(2),
    CLOCK_REQUEST(3),
    CLOCK_RESPONSE(4),
    SCREEN_SNAPSHOT(5),
    SCREEN_UPSERT(6),
    SCREEN_DELETE(7),
    PLAYBACK_UPDATE(8),
    OPERATION_RESULT(9);

    private final int id;

    MessageType(int id) {
        this.id = id;
    }

    public int id() {
        return id;
    }

    public static MessageType fromId(int id) throws ProtocolException {
        for (MessageType type : values()) {
            if (type.id == id) {
                return type;
            }
        }
        throw new ProtocolException("Unknown message type: " + id);
    }
}
