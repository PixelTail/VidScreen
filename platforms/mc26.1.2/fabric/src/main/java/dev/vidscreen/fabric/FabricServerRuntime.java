package dev.vidscreen.fabric;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.logging.LogUtils;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

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

final class FabricServerRuntime {
    private static final Logger LOGGER = LogUtils.getLogger();
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

    private FabricServerRuntime() {
    }

    static void initialize() {
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            Path file = server.getWorldPath(LevelResource.ROOT).resolve("vidscreen").resolve("screens.bin");
            ScreenService service = new ScreenService(new FileScreenRepository(file));
            try {
                service.load();
                screens = service;
                LOGGER.info("VidScreen loaded {} Fabric server screens", service.snapshot().size());
            } catch (IOException error) {
                throw new IllegalStateException("Could not load VidScreen screens", error);
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            screens = null;
            CLIENT_CAPABILITIES.clear();
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                CLIENT_CAPABILITIES.remove(handler.player.getUUID()));

        ServerPlayNetworking.registerGlobalReceiver(VidScreenPayload.TYPE, (payload, context) -> {
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
                    send(context, new ServerHello(
                            ProtocolVersion.MAJOR,
                            ProtocolVersion.MINOR,
                            accepted,
                            enabled,
                            System.currentTimeMillis(),
                            accepted ? "ok" : "incompatible_protocol"));
                    if (accepted) {
                        CLIENT_CAPABILITIES.put(playerId, enabled);
                        send(context, new ScreenSnapshot(service.revision(), Collections.<ScreenState>emptyList()));
                        for (ScreenState screen : service.snapshot()) {
                            if (CapabilityRequirements.supportsMedia(enabled, screen.media())) {
                                send(context, new ScreenUpsert(screen));
                            }
                        }
                    } else {
                        CLIENT_CAPABILITIES.remove(playerId);
                    }
                } else if (message instanceof ClockRequest request
                        && CLIENT_CAPABILITIES.containsKey(context.player().getUUID())) {
                    long received = System.currentTimeMillis();
                    send(context, new ClockResponse(
                            request.requestId(),
                            request.clientSendTimeMillis(),
                            received,
                            System.currentTimeMillis()));
                }
            } catch (ProtocolException | RuntimeException error) {
                LOGGER.warn("Rejected VidScreen packet from {}: {}", context.player().getUUID(), error.getMessage());
            }
        });
    }

    private static void send(ServerPlayNetworking.Context context, WireMessage message) throws ProtocolException {
        context.responseSender().sendPacket(new VidScreenPayload(CODEC.encode(message)));
    }
}
