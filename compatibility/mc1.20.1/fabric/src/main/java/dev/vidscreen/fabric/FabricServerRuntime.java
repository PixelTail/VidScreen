package dev.vidscreen.fabric;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.WorldSavePath;
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
    private static final long SERVER_CAPABILITIES = Capabilities.MP4
            | Capabilities.HLS
            | Capabilities.BILIBILI
            | Capabilities.YOUTUBE
            | Capabilities.TWITCH
            | Capabilities.LIVE_STREAMS
            | Capabilities.SPATIAL_AUDIO;
    private static final Map<UUID, Long> CLIENT_CAPABILITIES = new ConcurrentHashMap<UUID, Long>();
    private static volatile ScreenService screens;

    private FabricServerRuntime() {
    }

    static void initialize() {
        ServerLifecycleEvents.SERVER_STARTING.register(FabricServerRuntime::start);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            screens = null;
            CLIENT_CAPABILITIES.clear();
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                CLIENT_CAPABILITIES.remove(handler.getPlayer().getUuid()));

        ServerPlayNetworking.registerGlobalReceiver(VidScreenPayload.CHANNEL,
                (server, player, handler, buffer, responseSender) -> {
                    final byte[] data;
                    try {
                        data = VidScreenPayload.read(buffer).data();
                    } catch (RuntimeException error) {
                        LOGGER.warn("Rejected malformed VidScreen packet from {}: {}",
                                player.getUuid(), error.getMessage());
                        return;
                    }
                    server.execute(() -> handle(server, player, data));
                });
    }

    private static void start(MinecraftServer server) {
        Path file = server.getSavePath(WorldSavePath.ROOT).resolve("vidscreen").resolve("screens.bin");
        ScreenService service = new ScreenService(new FileScreenRepository(file));
        try {
            service.load();
            screens = service;
            LOGGER.info("VidScreen loaded {} Fabric 1.20.1 screens", service.snapshot().size());
        } catch (IOException error) {
            throw new IllegalStateException("Could not load VidScreen screens", error);
        }
    }

    private static void handle(MinecraftServer server, ServerPlayerEntity player, byte[] data) {
        ScreenService service = screens;
        if (service == null) {
            return;
        }
        try {
            WireMessage message = CODEC.decode(data);
            if (message instanceof ClientHello) {
                handleHello(player, service, (ClientHello) message);
            } else if (message instanceof ClockRequest
                    && CLIENT_CAPABILITIES.containsKey(player.getUuid())) {
                long received = System.currentTimeMillis();
                send(player, new ClockResponse(
                        ((ClockRequest) message).requestId(),
                        ((ClockRequest) message).clientSendTimeMillis(),
                        received,
                        System.currentTimeMillis()));
            }
        } catch (ProtocolException | RuntimeException error) {
            LOGGER.warn("Rejected VidScreen payload from {}: {}", player.getUuid(), error.getMessage());
        }
    }

    private static void handleHello(ServerPlayerEntity player, ScreenService service, ClientHello hello)
            throws ProtocolException {
        boolean accepted = hello.protocolMajor() == ProtocolVersion.MAJOR;
        long enabled = hello.capabilities() & SERVER_CAPABILITIES;
        send(player, new ServerHello(
                ProtocolVersion.MAJOR,
                ProtocolVersion.MINOR,
                accepted,
                enabled,
                System.currentTimeMillis(),
                accepted ? "ok" : "incompatible_protocol"));
        if (!accepted) {
            CLIENT_CAPABILITIES.remove(player.getUuid());
            return;
        }
        CLIENT_CAPABILITIES.put(player.getUuid(), enabled);
        send(player, new ScreenSnapshot(service.revision(), Collections.<ScreenState>emptyList()));
        for (ScreenState screen : service.snapshot()) {
            if (CapabilityRequirements.supportsMedia(enabled, screen.media())) {
                send(player, new ScreenUpsert(screen));
            }
        }
    }

    private static void send(ServerPlayerEntity player, WireMessage message) throws ProtocolException {
        ServerPlayNetworking.send(player, VidScreenPayload.CHANNEL, new VidScreenPayload(CODEC.encode(message)).toBuffer());
    }
}
