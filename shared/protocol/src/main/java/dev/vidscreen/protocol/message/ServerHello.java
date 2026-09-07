package dev.vidscreen.protocol.message;

import java.util.Objects;

import dev.vidscreen.protocol.MessageType;
import dev.vidscreen.protocol.WireMessage;

public final class ServerHello implements WireMessage {
    private final int protocolMajor;
    private final int protocolMinor;
    private final boolean accepted;
    private final long enabledCapabilities;
    private final long serverTimeMillis;
    private final String message;

    public ServerHello(
            int protocolMajor,
            int protocolMinor,
            boolean accepted,
            long enabledCapabilities,
            long serverTimeMillis,
            String message) {
        if (protocolMajor < 0 || protocolMajor > 65_535 || protocolMinor < 0 || protocolMinor > 65_535) {
            throw new IllegalArgumentException("Protocol version components must be between 0 and 65535");
        }
        this.protocolMajor = protocolMajor;
        this.protocolMinor = protocolMinor;
        this.accepted = accepted;
        this.enabledCapabilities = enabledCapabilities;
        this.serverTimeMillis = serverTimeMillis;
        this.message = Objects.requireNonNull(message, "message");
    }

    @Override
    public MessageType type() {
        return MessageType.SERVER_HELLO;
    }

    public int protocolMajor() {
        return protocolMajor;
    }

    public int protocolMinor() {
        return protocolMinor;
    }

    public boolean accepted() {
        return accepted;
    }

    public long enabledCapabilities() {
        return enabledCapabilities;
    }

    public long serverTimeMillis() {
        return serverTimeMillis;
    }

    public String message() {
        return message;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ServerHello)) {
            return false;
        }
        ServerHello that = (ServerHello) other;
        return protocolMajor == that.protocolMajor
                && protocolMinor == that.protocolMinor
                && accepted == that.accepted
                && enabledCapabilities == that.enabledCapabilities
                && serverTimeMillis == that.serverTimeMillis
                && message.equals(that.message);
    }

    @Override
    public int hashCode() {
        return Objects.hash(protocolMajor, protocolMinor, accepted, enabledCapabilities, serverTimeMillis, message);
    }
}
