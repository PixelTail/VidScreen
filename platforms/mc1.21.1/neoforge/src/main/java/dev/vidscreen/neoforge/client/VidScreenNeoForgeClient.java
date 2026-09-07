package dev.vidscreen.neoforge.client;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.slf4j.Logger;

import dev.vidscreen.domain.ClockOffsetEstimator;
import dev.vidscreen.domain.ClockSample;
import dev.vidscreen.neoforge.VidScreenNeoForge;
import dev.vidscreen.neoforge.VidScreenPayload;
import dev.vidscreen.protocol.ProtocolException;
import dev.vidscreen.protocol.ProtocolVersion;
import dev.vidscreen.protocol.WireCodec;
import dev.vidscreen.protocol.WireMessage;
import dev.vidscreen.protocol.message.ClientHello;
import dev.vidscreen.protocol.message.ClockRequest;
import dev.vidscreen.protocol.message.ClockResponse;
import dev.vidscreen.protocol.message.ServerHello;

@Mod(value = VidScreenNeoForge.MOD_ID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = VidScreenNeoForge.MOD_ID, value = Dist.CLIENT)
public final class VidScreenNeoForgeClient {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final WireCodec CODEC = new WireCodec();
    private static final ClientScreenStore SCREENS = new ClientScreenStore();
    private static final ClockOffsetEstimator CLOCK = new ClockOffsetEstimator(8);
    private static final long CLOCK_SAMPLE_INTERVAL_MILLIS = 5_000;

    private static ClientPlaybackController playback;
    private static long nextClockSampleMillis;
    private static long clockRequestId;

    public VidScreenNeoForgeClient(IEventBus modBus) {
        playback = new ClientPlaybackController(SCREENS, LOGGER);
        modBus.addListener(this::registerClientPayloads);
        ScreenRenderer.initialize(SCREENS, playback.textures());
    }

    private void registerClientPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar(Integer.toString(ProtocolVersion.MAJOR))
                .playToClient(VidScreenPayload.TYPE, VidScreenPayload.CODEC, this::handleClientPayload);
    }

    private void handleClientPayload(VidScreenPayload payload, IPayloadContext context) {
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
    }

    @SubscribeEvent
    static void login(ClientPlayerNetworkEvent.LoggingIn event) {
        SCREENS.clear();
        CLOCK.clear();
        playback.clear();
        nextClockSampleMillis = 0;
        try {
            PacketDistributor.sendToServer(new VidScreenPayload(CODEC.encode(new ClientHello(
                    ProtocolVersion.MAJOR,
                    ProtocolVersion.MINOR,
                    "0.1.0",
                    "neoforge",
                    "1.21.1",
                    playback.capabilities(),
                    4_096))));
        } catch (ProtocolException error) {
            LOGGER.error("Could not encode VidScreen client handshake", error);
        }
    }

    @SubscribeEvent
    static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        SCREENS.clear();
        CLOCK.clear();
        playback.clear();
    }

    @SubscribeEvent
    static void tick(ClientTickEvent.Post event) {
        long now = System.currentTimeMillis();
        playback.tick(estimatedServerTimeMillis(now));
        if (now < nextClockSampleMillis || Minecraft.getInstance().getConnection() == null) {
            return;
        }
        nextClockSampleMillis = now + CLOCK_SAMPLE_INTERVAL_MILLIS;
        try {
            PacketDistributor.sendToServer(
                    new VidScreenPayload(CODEC.encode(new ClockRequest(++clockRequestId, now))));
        } catch (ProtocolException | IllegalStateException error) {
            LOGGER.debug("Could not send VidScreen clock sample request: {}", error.getMessage());
        }
    }

    private static long estimatedServerTimeMillis(long clientTimeMillis) {
        return CLOCK.hasEstimate() ? CLOCK.estimateServerTimeMillis(clientTimeMillis) : clientTimeMillis;
    }
}
