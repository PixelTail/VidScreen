package dev.vidscreen.client;

import dev.vidscreen.domain.ClockOffsetEstimator;
import dev.vidscreen.domain.ClockSample;
import dev.vidscreen.protocol.ProtocolVersion;
import dev.vidscreen.protocol.Capabilities;
import dev.vidscreen.protocol.WireMessage;
import dev.vidscreen.protocol.message.ClientHello;
import dev.vidscreen.protocol.message.ClockRequest;
import dev.vidscreen.protocol.message.ClockResponse;
import dev.vidscreen.protocol.message.ServerHello;

/** Handshake retries, negotiated state and clock lifecycle independent of loader callbacks. */
public final class ClientConnection {
    public enum State { DISCONNECTED, CONNECTING, READY, REJECTED }

    private final ClientScreenStore screens;
    private final ClientHello hello;
    private final ClockOffsetEstimator clock = new ClockOffsetEstimator(8);
    private State state = State.DISCONNECTED;
    private long nextRequestMillis;
    private long requestId;
    private long pendingClockRequest = -1;
    private long pendingClockSent;
    private long enabledCapabilities;

    public ClientConnection(ClientScreenStore screens, String loader, String minecraftVersion, long capabilities) {
        this.screens = screens;
        hello = new ClientHello(ProtocolVersion.MAJOR, ProtocolVersion.MINOR,
                "0.1.0", loader, minecraftVersion, capabilities | Capabilities.SCREEN_EDITOR, 4096);
    }

    public synchronized void connect() {
        disconnect();
        state = State.CONNECTING;
    }

    public synchronized void disconnect() {
        state = State.DISCONNECTED;
        screens.clear();
        clock.clear();
        nextRequestMillis = 0;
        pendingClockRequest = -1;
        enabledCapabilities = 0;
    }

    public synchronized State state() {
        return state;
    }

    public synchronized boolean editorAvailable() {
        return state == State.READY && (enabledCapabilities & Capabilities.SCREEN_EDITOR) != 0;
    }

    /** Call only while the platform reports that the remote channel is available. */
    public synchronized WireMessage poll(long now) {
        if (now < nextRequestMillis) {
            return null;
        }
        if (state == State.CONNECTING) {
            nextRequestMillis = now + 2000;
            return hello;
        }
        if (state == State.READY) {
            nextRequestMillis = now + 5000;
            pendingClockRequest = ++requestId;
            pendingClockSent = now;
            return new ClockRequest(pendingClockRequest, now);
        }
        return null;
    }

    public synchronized void accept(WireMessage message, long now) {
        if (state == State.DISCONNECTED || state == State.REJECTED) {
            return;
        }
        if (message instanceof ServerHello) {
            ServerHello reply = (ServerHello) message;
            if (!reply.accepted() || reply.protocolMajor() != ProtocolVersion.MAJOR) {
                screens.clear();
                clock.clear();
                state = State.REJECTED;
            } else {
                state = State.READY;
                enabledCapabilities = reply.enabledCapabilities() & hello.capabilities();
                nextRequestMillis = 0;
            }
        } else if (state == State.READY) {
            if (message instanceof ClockResponse) {
                ClockResponse reply = (ClockResponse) message;
                if (reply.requestId() == pendingClockRequest && reply.clientSendTimeMillis() == pendingClockSent) {
                    clock.add(new ClockSample(reply.clientSendTimeMillis(), reply.serverReceiveTimeMillis(),
                            reply.serverSendTimeMillis(), now));
                    pendingClockRequest = -1;
                }
            } else {
                screens.accept(message);
            }
        }
    }

    public synchronized long estimatedServerTimeMillis(long now) {
        return clock.hasEstimate() ? clock.estimateServerTimeMillis(now) : now;
    }
}
