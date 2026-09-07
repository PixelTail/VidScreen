package dev.vidscreen.domain;

public final class ClockSample {
    private final long clientSendMillis;
    private final long serverReceiveMillis;
    private final long serverSendMillis;
    private final long clientReceiveMillis;

    public ClockSample(
            long clientSendMillis,
            long serverReceiveMillis,
            long serverSendMillis,
            long clientReceiveMillis) {
        if (clientReceiveMillis < clientSendMillis) {
            throw new IllegalArgumentException("Client receive time precedes send time");
        }
        if (serverSendMillis < serverReceiveMillis) {
            throw new IllegalArgumentException("Server send time precedes receive time");
        }
        this.clientSendMillis = clientSendMillis;
        this.serverReceiveMillis = serverReceiveMillis;
        this.serverSendMillis = serverSendMillis;
        this.clientReceiveMillis = clientReceiveMillis;
    }

    public long roundTripMillis() {
        return Math.max(0,
                (clientReceiveMillis - clientSendMillis) - (serverSendMillis - serverReceiveMillis));
    }

    public long serverOffsetMillis() {
        return Math.round(((serverReceiveMillis - clientSendMillis)
                + (serverSendMillis - clientReceiveMillis)) / 2.0);
    }
}
