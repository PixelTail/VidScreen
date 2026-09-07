package dev.vidscreen.forge.client;

import com.mojang.logging.LogUtils;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import net.minecraft.client.Minecraft;

import dev.vidscreen.forge.VidScreenForge;
import dev.vidscreen.forge.VidScreenMessage;
import dev.vidscreen.forge.VidScreenNetwork;
import dev.vidscreen.domain.ClockOffsetEstimator;
import dev.vidscreen.domain.ClockSample;
import dev.vidscreen.protocol.ProtocolException;
import dev.vidscreen.protocol.ProtocolVersion;
import dev.vidscreen.protocol.WireCodec;
import dev.vidscreen.protocol.WireMessage;
import dev.vidscreen.protocol.message.ClientHello;
import dev.vidscreen.protocol.message.ClockRequest;
import dev.vidscreen.protocol.message.ClockResponse;
import dev.vidscreen.protocol.message.ServerHello;

@Mod.EventBusSubscriber(modid = VidScreenForge.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.MOD)
public final class VidScreenClient {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final WireCodec CODEC = new WireCodec();
    private static final ClientScreenStore SCREENS = new ClientScreenStore();
    private static final ClockOffsetEstimator CLOCK = new ClockOffsetEstimator(8);
    private static ClientPlaybackController playback;
    private static ScreenRenderer renderer;
    private static long nextClockSample;
    private static long clockRequestId;
    private static boolean initialized;

    private VidScreenClient() {
    }

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            if (initialized) {
                return;
            }
            initialized = true;
            playback = new ClientPlaybackController(SCREENS, LOGGER);
            renderer = new ScreenRenderer(SCREENS, playback.textures());
            VidScreenNetwork.setClientHandler(VidScreenClient::accept);
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(VidScreenClient.class);
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(renderer);
        });
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END && initialized) {
            tick();
        }
    }

    @SubscribeEvent
    public static void onLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        if (initialized) {
            join();
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        if (initialized) {
            SCREENS.clear();
            CLOCK.clear();
            playback.clear();
        }
    }

    private static void join() {
        SCREENS.clear();
        CLOCK.clear();
        playback.clear();
        nextClockSample = 0;
        try {
            send(new ClientHello(ProtocolVersion.MAJOR, ProtocolVersion.MINOR, "0.1.0", "forge",
                    "1.20.1", playback.capabilities(), 4_096));
        } catch (ProtocolException error) {
            LOGGER.error("Could not encode VidScreen Forge client handshake", error);
        }
    }

    private static void accept(byte[] data) {
        try {
            WireMessage message = CODEC.decode(data);
            if (message instanceof ServerHello && !((ServerHello) message).accepted()) {
                ServerHello hello = (ServerHello) message;
                LOGGER.error("VidScreen server rejected protocol {}.{}: {}",
                        hello.protocolMajor(), hello.protocolMinor(), hello.message());
                SCREENS.clear();
            } else {
                if (message instanceof ClockResponse) {
                    ClockResponse response = (ClockResponse) message;
                    CLOCK.add(new ClockSample(response.clientSendTimeMillis(),
                            response.serverReceiveTimeMillis(), response.serverSendTimeMillis(),
                            System.currentTimeMillis()));
                }
                SCREENS.accept(message);
            }
        } catch (ProtocolException | RuntimeException error) {
            LOGGER.warn("Rejected VidScreen Forge server payload: {}", error.getMessage());
        }
    }

    private static void tick() {
        long now = System.currentTimeMillis();
        playback.tick(CLOCK.hasEstimate() ? CLOCK.estimateServerTimeMillis(now) : now);
        if (now < nextClockSample) {
            return;
        }
        nextClockSample = now + 5_000;
        try {
            send(new ClockRequest(++clockRequestId, now));
        } catch (ProtocolException | IllegalStateException error) {
            LOGGER.debug("Could not send VidScreen clock sample: {}", error.getMessage());
        }
    }

    private static void send(WireMessage message) throws ProtocolException {
        VidScreenNetwork.sendToServer(CODEC.encode(message));
    }
}
