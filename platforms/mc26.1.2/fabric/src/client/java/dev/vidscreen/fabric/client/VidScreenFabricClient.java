package dev.vidscreen.fabric.client;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import dev.vidscreen.domain.ClockOffsetEstimator;
import dev.vidscreen.domain.ClockSample;
import dev.vidscreen.fabric.VidScreenPayload;
import dev.vidscreen.protocol.ProtocolException;
import dev.vidscreen.protocol.ProtocolVersion;
import dev.vidscreen.protocol.WireCodec;
import dev.vidscreen.protocol.WireMessage;
import dev.vidscreen.protocol.message.ClientHello;
import dev.vidscreen.protocol.message.ClockRequest;
import dev.vidscreen.protocol.message.ClockResponse;
import dev.vidscreen.protocol.message.ServerHello;

public final class VidScreenFabricClient implements ClientModInitializer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final WireCodec CODEC = new WireCodec();
    private static final ClientScreenStore SCREENS = new ClientScreenStore();
    private static final ClockOffsetEstimator CLOCK = new ClockOffsetEstimator(8);
    private static final long CLOCK_SAMPLE_INTERVAL_MILLIS = 5_000;

    private static ClientPlaybackController playback;
    private static long nextClockSampleMillis;
    private static long clockRequestId;

    @Override
    public void onInitializeClient() {
        playback = new ClientPlaybackController(SCREENS, LOGGER);
        ClientPlayNetworking.registerGlobalReceiver(VidScreenPayload.TYPE, (payload, context) -> {
            try {
                WireMessage message = CODEC.decode(payload.data());
                if (message instanceof ServerHello hello && !hello.accepted()) {
                    LOGGER.error("VidScreen server rejected protocol {}.{}: {}",
                            hello.protocolMajor(), hello.protocolMinor(), hello.message());
                    SCREENS.clear();
                } else {
                    if (message instanceof ClockResponse clock) {
                        CLOCK.add(new ClockSample(
                                clock.clientSendTimeMillis(),
                                clock.serverReceiveTimeMillis(),
                                clock.serverSendTimeMillis(),
                                System.currentTimeMillis()));
                    }
                    SCREENS.accept(message);
                }
            } catch (ProtocolException | RuntimeException error) {
                LOGGER.warn("Rejected VidScreen server payload: {}", error.getMessage());
            }
        });

        ClientPlayConnectionEvents.JOIN.register((listener, sender, client) -> {
            SCREENS.clear();
            CLOCK.clear();
            playback.clear();
            nextClockSampleMillis = 0;
            try {
                sender.sendPacket(new VidScreenPayload(CODEC.encode(new ClientHello(
                        ProtocolVersion.MAJOR,
                        ProtocolVersion.MINOR,
                        "0.1.0",
                        "fabric",
                        "26.1.2",
                        playback.capabilities(),
                        4_096))));
            } catch (ProtocolException error) {
                LOGGER.error("Could not encode VidScreen client handshake", error);
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((listener, client) -> {
            SCREENS.clear();
            CLOCK.clear();
            playback.clear();
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick());

        ScreenRenderer.initialize(SCREENS, playback.textures());
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            ScreenRenderer.close();
            playback.close();
        });
    }

    private static void tick() {
        long now = System.currentTimeMillis();
        playback.tick(estimatedServerTimeMillis(now));
        if (now < nextClockSampleMillis || !ClientPlayNetworking.canSend(VidScreenPayload.TYPE)) {
            return;
        }
        nextClockSampleMillis = now + CLOCK_SAMPLE_INTERVAL_MILLIS;
        try {
            ClientPlayNetworking.send(new VidScreenPayload(CODEC.encode(new ClockRequest(++clockRequestId, now))));
        } catch (ProtocolException | IllegalStateException error) {
            LOGGER.debug("Could not send VidScreen clock sample request: {}", error.getMessage());
        }
    }

    static long estimatedServerTimeMillis(long clientTimeMillis) {
        return CLOCK.hasEstimate() ? CLOCK.estimateServerTimeMillis(clientTimeMillis) : clientTimeMillis;
    }
}
