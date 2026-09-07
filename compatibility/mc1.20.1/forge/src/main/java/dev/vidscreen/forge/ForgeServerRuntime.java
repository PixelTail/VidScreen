package dev.vidscreen.forge;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.slf4j.Logger;

import dev.vidscreen.domain.ScreenState;
import dev.vidscreen.protocol.Capabilities;
import dev.vidscreen.protocol.CapabilityRequirements;
import dev.vidscreen.protocol.ProtocolException;
import dev.vidscreen.protocol.ProtocolVersion;
import dev.vidscreen.protocol.WireCodec;
import dev.vidscreen.protocol.WireMessage;
import dev.vidscreen.protocol.message.ClientHello;
import dev.vidscreen.protocol.message.ClockRequest;
import dev.vidscreen.protocol.message.ClockResponse;
import dev.vidscreen.protocol.message.ScreenSnapshot;
import dev.vidscreen.protocol.message.ScreenUpsert;
import dev.vidscreen.protocol.message.ServerHello;
import dev.vidscreen.server.FileScreenRepository;
import dev.vidscreen.server.ScreenService;

final class ForgeServerRuntime {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final WireCodec CODEC = new WireCodec();
    private static final long SERVER_CAPABILITIES = Capabilities.MP4
            | Capabilities.HLS | Capabilities.BILIBILI | Capabilities.YOUTUBE
            | Capabilities.TWITCH | Capabilities.LIVE_STREAMS | Capabilities.SPATIAL_AUDIO;
    private static final Map<UUID, Long> CLIENT_CAPABILITIES = new ConcurrentHashMap<UUID, Long>();
    private static volatile ScreenService screens;

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        Path file = event.getServer().getWorldPath(LevelResource.ROOT)
                .resolve("vidscreen").resolve("screens.bin");
        ScreenService service = new ScreenService(new FileScreenRepository(file));
        try {
            service.load();
            screens = service;
            LOGGER.info("VidScreen loaded {} Forge 1.20.1 screens", service.snapshot().size());
        } catch (IOException error) {
            throw new IllegalStateException("Could not load VidScreen screens", error);
        }
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        screens = null;
        CLIENT_CAPABILITIES.clear();
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        CLIENT_CAPABILITIES.remove(event.getEntity().getUUID());
    }

    static void handle(ServerPlayer player, byte[] data) {
        ScreenService service = screens;
        if (service == null) {
            return;
        }
        try {
            WireMessage message = CODEC.decode(data);
            if (message instanceof ClientHello) {
                handleHello(player, service, (ClientHello) message);
            } else if (message instanceof ClockRequest
                    && CLIENT_CAPABILITIES.containsKey(player.getUUID())) {
                ClockRequest request = (ClockRequest) message;
                send(player, new ClockResponse(request.requestId(), request.clientSendTimeMillis(),
                        System.currentTimeMillis(), System.currentTimeMillis()));
            }
        } catch (ProtocolException | RuntimeException error) {
            LOGGER.warn("Rejected VidScreen payload from {}: {}", player.getUUID(), error.getMessage());
        }
    }

    private static void handleHello(ServerPlayer player, ScreenService service, ClientHello hello)
            throws ProtocolException {
        boolean accepted = hello.protocolMajor() == ProtocolVersion.MAJOR;
        long enabled = hello.capabilities() & SERVER_CAPABILITIES;
        send(player, new ServerHello(ProtocolVersion.MAJOR, ProtocolVersion.MINOR, accepted, enabled,
                System.currentTimeMillis(), accepted ? "ok" : "incompatible_protocol"));
        if (!accepted) {
            CLIENT_CAPABILITIES.remove(player.getUUID());
            return;
        }
        CLIENT_CAPABILITIES.put(player.getUUID(), enabled);
        send(player, new ScreenSnapshot(service.revision(), Collections.<ScreenState>emptyList()));
        for (ScreenState screen : service.snapshot()) {
            if (CapabilityRequirements.supportsMedia(enabled, screen.media())) {
                send(player, new ScreenUpsert(screen));
            }
        }
    }

    private static void send(ServerPlayer player, WireMessage message) throws ProtocolException {
        VidScreenNetwork.sendTo(player, CODEC.encode(message));
    }
}
