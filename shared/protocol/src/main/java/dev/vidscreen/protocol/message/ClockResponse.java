package dev.vidscreen.protocol.message;

import dev.vidscreen.protocol.MessageType;
import dev.vidscreen.protocol.WireMessage;

public final class ClockResponse implements WireMessage {
    private final long requestId;
    private final long clientSendTimeMillis;
    private final long serverReceiveTimeMillis;
    private final long serverSendTimeMillis;

    public ClockResponse(
            long requestId,
            long clientSendTimeMillis,
            long serverReceiveTimeMillis,
            long serverSendTimeMillis) {
        this.requestId = requestId;
        this.clientSendTimeMillis = clientSendTimeMillis;
        this.serverReceiveTimeMillis = serverReceiveTimeMillis;
        this.serverSendTimeMillis = serverSendTimeMillis;
    }

    @Override
    public MessageType type() {
        return MessageType.CLOCK_RESPONSE;
    }

    public long requestId() {
        return requestId;
    }

    public long clientSendTimeMillis() {
        return clientSendTimeMillis;
    }

    public long serverReceiveTimeMillis() {
        return serverReceiveTimeMillis;
    }

    public long serverSendTimeMillis() {
        return serverSendTimeMillis;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ClockResponse)) {
            return false;
        }
        ClockResponse that = (ClockResponse) other;
        return requestId == that.requestId
                && clientSendTimeMillis == that.clientSendTimeMillis
                && serverReceiveTimeMillis == that.serverReceiveTimeMillis
                && serverSendTimeMillis == that.serverSendTimeMillis;
    }

    @Override
    public int hashCode() {
        int result = Long.hashCode(requestId);
        result = 31 * result + Long.hashCode(clientSendTimeMillis);
        result = 31 * result + Long.hashCode(serverReceiveTimeMillis);
        return 31 * result + Long.hashCode(serverSendTimeMillis);
    }
}
