package dev.vidscreen.protocol.message;

import dev.vidscreen.protocol.MessageType;
import dev.vidscreen.protocol.WireMessage;

public final class ClockRequest implements WireMessage {
    private final long requestId;
    private final long clientSendTimeMillis;

    public ClockRequest(long requestId, long clientSendTimeMillis) {
        this.requestId = requestId;
        this.clientSendTimeMillis = clientSendTimeMillis;
    }

    @Override
    public MessageType type() {
        return MessageType.CLOCK_REQUEST;
    }

    public long requestId() {
        return requestId;
    }

    public long clientSendTimeMillis() {
        return clientSendTimeMillis;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof ClockRequest
                && requestId == ((ClockRequest) other).requestId
                && clientSendTimeMillis == ((ClockRequest) other).clientSendTimeMillis;
    }

    @Override
    public int hashCode() {
        return 31 * Long.hashCode(requestId) + Long.hashCode(clientSendTimeMillis);
    }
}
