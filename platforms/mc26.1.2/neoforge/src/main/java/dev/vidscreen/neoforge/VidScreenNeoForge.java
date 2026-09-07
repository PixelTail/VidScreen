package dev.vidscreen.neoforge;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
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

@Mod(VidScreenNeoForge.MOD_ID)
public final class VidScreenNeoForge {
    public static final String MOD_ID = "vidscreen";
    public static final Logger LOGGER = LogUtils.getLogger();

    private static final WireCodec CODEC = new WireCodec();
    private static final long CAPABILITIES = Capabilities.MP4
            | Capabilities.HLS
            | Capabilities.BILIBILI
            | Capabilities.YOUTUBE
            | Capabilities.TWITCH
            | Capabilities.LIVE_STREAMS
            | Capabilities.SPATIAL_AUDIO;

    private static final Map<UUID, Long> CLIENT_CAPABILITIES = new ConcurrentHashMap<>();
    private static volatile ScreenService screens;

    public VidScreenNeoForge(IEventBus modBus) {
        modBus.addListener(this::registerPayloads);
        NeoForge.EVENT_BUS.addListener(this::serverStarting);
        NeoForge.EVENT_BUS.addListener(this::serverStopped);
        NeoForge.EVENT_BUS.addListener(this::playerLoggedOut);
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar(Integer.toString(ProtocolVersion.MAJOR))
                .playBidirectional(VidScreenPayload.TYPE, VidScreenPayload.CODEC, this::handleServerPayload);
    }

    private void serverStarting(ServerStartingEvent event) {
        MinecraftServer server = event.getServer();
        Path file = server.getWorldPath(LevelResource.ROOT).resolve("vidscreen").resolve("screens.bin");
        ScreenService service = new ScreenService(new FileScreenRepository(file));
        try {
            service.load();
            screens = service;
            LOGGER.info("VidScreen loaded {} NeoForge server screens", service.snapshot().size());
        } catch (IOException error) {
            throw new IllegalStateException("Could not load VidScreen screens", error);
        }
    }

    private void serverStopped(ServerStoppedEvent event) {
        screens = null;
        CLIENT_CAPABILITIES.clear();
    }

    private void playerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        CLIENT_CAPABILITIES.remove(event.getEntity().getUUID());
    }

    private void handleServerPayload(VidScreenPayload payload, IPayloadContext context) {
        ScreenService service = screens;
        if (service == null) {
            return;
        }
        try {
            WireMessage message = CODEC.decode(payload.data());
            if (message instanceof ClientHello hello) {
                UUID playerId = context.player().getUUID();
                boolean accepted = hello.protocolMajor() == ProtocolVersion.MAJOR;
                long enabled = hello.capabilities() & CAPABILITIES;
                reply(context, new ServerHello(
                        ProtocolVersion.MAJOR,
                        ProtocolVersion.MINOR,
                        accepted,
                        enabled,
                        System.currentTimeMillis(),
                        accepted ? "ok" : "incompatible_protocol"));
                if (accepted) {
                    CLIENT_CAPABILITIES.put(playerId, enabled);
                    reply(context, new ScreenSnapshot(service.revision(), Collections.<ScreenState>emptyList()));
                    for (ScreenState screen : service.snapshot()) {
                        if (CapabilityRequirements.supportsMedia(enabled, screen.media())) {
                            reply(context, new ScreenUpsert(screen));
                        }
                    }
                } else {
                    CLIENT_CAPABILITIES.remove(playerId);
                }
            } else if (message instanceof ClockRequest request
                    && CLIENT_CAPABILITIES.containsKey(context.player().getUUID())) {
                long received = System.currentTimeMillis();
                reply(context, new ClockResponse(
                        request.requestId(),
                        request.clientSendTimeMillis(),
                        received,
                        System.currentTimeMillis()));
            }
        } catch (ProtocolException | RuntimeException error) {
            LOGGER.warn("Rejected VidScreen packet from {}: {}", context.player().getUUID(), error.getMessage());
        }
    }

    private static void reply(IPayloadContext context, WireMessage message) throws ProtocolException {
        context.reply(new VidScreenPayload(CODEC.encode(message)));
    }
}
