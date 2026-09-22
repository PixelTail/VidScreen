package dev.vidscreen.neoforge;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.slf4j.Logger;

import dev.vidscreen.domain.ScreenState;
import dev.vidscreen.domain.DimensionKey;
import dev.vidscreen.protocol.Capabilities;
import dev.vidscreen.protocol.CapabilityRequirements;
import dev.vidscreen.protocol.ProtocolException;
import dev.vidscreen.protocol.ProtocolVersion;
import dev.vidscreen.protocol.WireCodec;
import dev.vidscreen.protocol.WireMessage;
import dev.vidscreen.protocol.message.ClientHello;
import dev.vidscreen.protocol.message.ClockRequest;
import dev.vidscreen.protocol.message.ClockResponse;
import dev.vidscreen.protocol.message.EditorRequest;
import dev.vidscreen.protocol.message.OperationResult;
import dev.vidscreen.protocol.message.PlaybackUpdate;
import dev.vidscreen.protocol.message.ScreenDelete;
import dev.vidscreen.protocol.message.ScreenSnapshot;
import dev.vidscreen.protocol.message.ScreenUpsert;
import dev.vidscreen.protocol.message.SceneSnapshot;
import dev.vidscreen.protocol.message.SceneUpsert;
import dev.vidscreen.protocol.message.ServerHello;
import dev.vidscreen.server.FileScreenRepository;
import dev.vidscreen.server.ScreenEditorDispatcher;
import dev.vidscreen.server.ScreenEditorService;
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
            | Capabilities.SPATIAL_AUDIO
            | Capabilities.SCREEN_EDITOR;

    private static final Map<UUID, Long> CLIENT_CAPABILITIES = new ConcurrentHashMap<>();
    private static volatile ScreenService screens;
    private static volatile MinecraftServer currentServer;
    private static volatile ScreenEditorDispatcher editorDispatcher;

    public VidScreenNeoForge(IEventBus modBus) {
        modBus.addListener(this::registerPayloads);
        NeoForge.EVENT_BUS.addListener(this::serverStarting);
        NeoForge.EVENT_BUS.addListener(this::serverStopped);
        NeoForge.EVENT_BUS.addListener(this::playerLoggedOut);
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar(Integer.toString(ProtocolVersion.MAJOR)).optional()
                .playBidirectional(VidScreenPayload.TYPE, VidScreenPayload.CODEC, this::handleServerPayload);
    }

    private void serverStarting(ServerStartingEvent event) {
        MinecraftServer server = event.getServer();
        Path file = server.getWorldPath(LevelResource.ROOT).resolve("vidscreen").resolve("screens.bin");
        ScreenService service = new ScreenService(new FileScreenRepository(file));
        try {
            service.load();
            screens = service;
            currentServer = server;
            editorDispatcher = new ScreenEditorDispatcher(new ScreenEditorService(service));
            LOGGER.info("VidScreen loaded {} NeoForge server screens", service.snapshot().size());
        } catch (IOException error) {
            throw new IllegalStateException("Could not load VidScreen screens", error);
        }
    }

    private void serverStopped(ServerStoppedEvent event) {
        currentServer = null;
        screens = null;
        CLIENT_CAPABILITIES.clear();
        ScreenEditorDispatcher dispatcher = editorDispatcher;
        editorDispatcher = null;
        if (dispatcher != null) {
            ScreenEditorDispatcher.ShutdownResult shutdown = dispatcher.shutdown();
            if (!shutdown.terminated()) {
                LOGGER.warn("VidScreen editor worker remained alive after server stop");
            }
        }
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
                    if (context.player() instanceof ServerPlayer) {
                        sendSnapshot((ServerPlayer) context.player(), service, enabled);
                    }
                } else {
                    CLIENT_CAPABILITIES.remove(playerId);
                }
            } else if (message instanceof EditorRequest request) {
                handleEditorRequest(context, request);
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
            LOGGER.warn("Rejected VidScreen packet from {} ({})",
                    context.player().getUUID(), error.getClass().getSimpleName());
        }
    }

    private static void handleEditorRequest(IPayloadContext context, EditorRequest request) {
        if (!(context.player() instanceof ServerPlayer)) {
            sendEditorResult(context, request, false, "unavailable", "The screen editor is only available to players.");
            return;
        }
        ServerPlayer player = (ServerPlayer) context.player();
        UUID playerId = player.getUUID();
        Long capabilities = CLIENT_CAPABILITIES.get(playerId);
        if (capabilities == null || (capabilities.longValue() & Capabilities.SCREEN_EDITOR) == 0) {
            sendEditorResult(context, request, false, "unsupported", "Screen editor is not negotiated.");
            return;
        }
        if (!player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
            sendEditorResult(context, request, false, "forbidden", "You do not have permission to edit screens.");
            return;
        }

        ScreenService service = screens;
        ScreenEditorDispatcher dispatcher = editorDispatcher;
        MinecraftServer server = currentServer;
        if (service == null || dispatcher == null || server == null || player.level().getServer() != server) {
            sendEditorResult(context, request, false, "unavailable", "The screen editor is not available.");
            return;
        }

        DimensionKey actorDimension;
        try {
            actorDimension = new DimensionKey(player.level().dimension().identifier().toString());
        } catch (RuntimeException error) {
            sendEditorResult(context, request, false, "invalid_dimension", "Your current dimension is unavailable.");
            return;
        }
        final ServerPlayer sessionPlayer = player;
        ScreenEditorDispatcher.SubmitResult submitted = dispatcher.submit(
                playerId,
                request,
                true,
                actorDimension,
                result -> {
                    try {
                        server.execute(() -> completeEditor(server, service, sessionPlayer, result));
                    } catch (RuntimeException ignored) {
                    }
                });
        if (submitted == ScreenEditorDispatcher.SubmitResult.BUSY) {
            sendEditorResult(context, request, false, "busy", "Finish the current screen operation first.");
        } else if (submitted == ScreenEditorDispatcher.SubmitResult.QUEUE_FULL) {
            sendEditorResult(context, request, false, "busy", "The screen editor is busy; try again shortly.");
        } else if (submitted == ScreenEditorDispatcher.SubmitResult.CLOSED) {
            sendEditorResult(context, request, false, "unavailable", "The screen editor is shutting down.");
        }
    }

    private static void completeEditor(
            MinecraftServer server,
            ScreenService service,
            ServerPlayer sessionPlayer,
            ScreenEditorService.Result result) {
        if (currentServer != server || screens != service) {
            return;
        }
        if (result.success()) {
            switch (result.change()) {
                case UPSERT:
                    broadcastUpsert(server, result.screen());
                    break;
                case PLAYBACK:
                    broadcastPlayback(server, result.screen());
                    break;
                case DELETE:
                    broadcastDelete(server, result.deletedScreenId(), result.revision());
                    break;
                case SCENE:
                    synchronizeAll(server, service);
                    break;
                case NONE: {
                    Long capabilities = CLIENT_CAPABILITIES.get(sessionPlayer.getUUID());
                    if (capabilities != null
                            && server.getPlayerList().getPlayer(sessionPlayer.getUUID()) == sessionPlayer
                            && !sessionPlayer.isRemoved()) {
                        sendSnapshot(sessionPlayer, service, capabilities.longValue());
                    }
                    break;
                }
                default:
                    break;
            }
        }
        if (server.getPlayerList().getPlayer(sessionPlayer.getUUID()) == sessionPlayer
                && !sessionPlayer.isRemoved()) {
            sendEditorResult(sessionPlayer, result);
        }
    }

    private static void sendEditorResult(
            IPayloadContext context,
            EditorRequest request,
            boolean success,
            String code,
            String message) {
        try {
            context.reply(new VidScreenPayload(CODEC.encode(
                    new OperationResult(request.operationId(), success, code, message))));
        } catch (ProtocolException error) {
            LOGGER.warn("Could not encode editor result", error);
        }
    }

    private static void sendEditorResult(ServerPlayer player, ScreenEditorService.Result result) {
        try {
            PacketDistributor.sendToPlayer(player, new VidScreenPayload(CODEC.encode(new OperationResult(
                    result.operationId(), result.success(), result.code(), result.message()))));
        } catch (ProtocolException | RuntimeException error) {
            LOGGER.warn("Could not send editor result", error);
        }
    }

    private static void synchronizeAll(MinecraftServer server, ScreenService service) {
        for (UUID playerId : CLIENT_CAPABILITIES.keySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null && !player.isRemoved()) {
                sendSnapshot(player, service, CLIENT_CAPABILITIES.get(playerId).longValue());
            }
        }
    }

    private static void broadcastUpsert(MinecraftServer server, ScreenState screen) {
        for (Map.Entry<UUID, Long> client : CLIENT_CAPABILITIES.entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(client.getKey());
            if (player == null || player.isRemoved()) {
                continue;
            }
            sendScreenUpdate(player, client.getValue().longValue(), screen);
        }
    }

    private static void broadcastPlayback(MinecraftServer server, ScreenState screen) {
        for (Map.Entry<UUID, Long> client : CLIENT_CAPABILITIES.entrySet()) {
            if (!CapabilityRequirements.supportsMedia(client.getValue().longValue(), screen.media())) {
                continue;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(client.getKey());
            if (player != null && !player.isRemoved()) {
                sendSafely(player, new PlaybackUpdate(screen.definition().id(), screen.playback()));
            }
        }
    }

    private static void broadcastDelete(MinecraftServer server, UUID screenId, long revision) {
        for (UUID playerId : CLIENT_CAPABILITIES.keySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null && !player.isRemoved()) {
                sendSafely(player, new ScreenDelete(screenId, revision));
            }
        }
    }

    private static void sendSnapshot(ServerPlayer player, ScreenService service, long capabilities) {
        try {
            SceneSnapshot scene = service.scene();
            if ((capabilities & Capabilities.SCREEN_EDITOR) != 0) {
                PacketDistributor.sendToPlayer(player, new VidScreenPayload(CODEC.encode(new SceneSnapshot(
                        scene.revision(), Collections.<ScreenState>emptyList(), new ArrayList<>(scene.areas())))));
                for (ScreenState screen : scene.screens()) {
                    PacketDistributor.sendToPlayer(player, new VidScreenPayload(CODEC.encode(new SceneUpsert(
                            CapabilityRequirements.visibleState(capabilities, screen)))));
                }
            } else {
                PacketDistributor.sendToPlayer(player, new VidScreenPayload(CODEC.encode(new ScreenSnapshot(
                        scene.revision(), Collections.<ScreenState>emptyList()))));
                for (ScreenState screen : scene.screens()) {
                    PacketDistributor.sendToPlayer(player, new VidScreenPayload(CODEC.encode(new ScreenUpsert(
                            CapabilityRequirements.visibleState(capabilities, screen)))));
                }
            }
        } catch (ProtocolException | RuntimeException error) {
            LOGGER.warn("Could not synchronize VidScreen scene", error);
        }
    }

    private static void sendScreenUpdate(ServerPlayer player, long capabilities, ScreenState screen) {
        try {
            ScreenState visible = CapabilityRequirements.visibleState(capabilities, screen);
            WireMessage message = (capabilities & Capabilities.SCREEN_EDITOR) != 0
                    ? new SceneUpsert(visible)
                    : new ScreenUpsert(visible);
            PacketDistributor.sendToPlayer(player, new VidScreenPayload(CODEC.encode(message)));
        } catch (ProtocolException | RuntimeException error) {
            LOGGER.warn("Could not send VidScreen screen update", error);
        }
    }

    private static void sendSafely(ServerPlayer player, WireMessage message) {
        try {
            PacketDistributor.sendToPlayer(player, new VidScreenPayload(CODEC.encode(message)));
        } catch (ProtocolException | RuntimeException error) {
            LOGGER.warn("Could not send VidScreen update", error);
        }
    }

    private static void reply(IPayloadContext context, WireMessage message) throws ProtocolException {
        context.reply(new VidScreenPayload(CODEC.encode(message)));
    }
}
