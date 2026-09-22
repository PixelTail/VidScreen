package dev.vidscreen.paper;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

import dev.vidscreen.domain.ScreenState;
import dev.vidscreen.protocol.Capabilities;
import dev.vidscreen.protocol.CapabilityRequirements;
import dev.vidscreen.protocol.ProtocolException;
import dev.vidscreen.protocol.PayloadFraming;
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
import dev.vidscreen.server.ScreenService;
import dev.vidscreen.server.ScreenEditorService;

final class PaperScreenMessenger implements PluginMessageListener, Listener {
    static final String CHANNEL = "vidscreen:main";
    private static final long SERVER_CAPABILITIES = Capabilities.MP4
            | Capabilities.HLS
            | Capabilities.BILIBILI
            | Capabilities.YOUTUBE
            | Capabilities.TWITCH
            | Capabilities.LIVE_STREAMS
            | Capabilities.SPATIAL_AUDIO
            | Capabilities.SCREEN_EDITOR;

    private final VidScreenPaperPlugin plugin;
    private final ScreenService screens;
    private final WireCodec codec = new WireCodec();
    private final Map<UUID, Long> clientCapabilities = new ConcurrentHashMap<UUID, Long>();
    private final Map<UUID, PlayerSession> clientSessions = new ConcurrentHashMap<UUID, PlayerSession>();
    private final Map<UUID, EditorOperation> editorInFlight = new ConcurrentHashMap<UUID, EditorOperation>();
    private final ScreenEditorService editor;

    PaperScreenMessenger(VidScreenPaperPlugin plugin, ScreenService screens) {
        this.plugin = plugin;
        this.screens = screens;
        this.editor = new ScreenEditorService(screens);
    }

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, @NotNull byte[] payload) {
        if (!CHANNEL.equals(channel)) {
            return;
        }

        try {
            WireMessage message = codec.decode(PayloadFraming.decode(payload));
            if (message instanceof ClientHello) {
                handleHello(player, (ClientHello) message);
            } else if (message instanceof ClockRequest && currentSession(player) != null) {
                handleClockRequest(player, (ClockRequest) message);
            } else if (message instanceof EditorRequest) {
                handleEditorRequest(player, (EditorRequest) message);
            }
        } catch (ProtocolException | RuntimeException error) {
            plugin.getLogger().log(Level.WARNING,
                    "Rejected VidScreen payload from " + player.getUniqueId()
                            + " (" + error.getClass().getSimpleName() + ")");
        }
    }

    private void handleHello(Player player, ClientHello hello) throws ProtocolException {
        UUID playerId = player.getUniqueId();
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
            PlayerSession current = clientSessions.get(playerId);
            if (current == null || current.player == player) {
                if (current == null) {
                    clientCapabilities.remove(playerId);
                } else {
                    clientSessions.remove(playerId, current);
                    clientCapabilities.remove(playerId, current.capabilities);
                }
            }
            return;
        }

        PlayerSession session = new PlayerSession(playerId, player, enabled);
        clientSessions.put(playerId, session);
        clientCapabilities.put(playerId, enabled);
        sendSnapshot(player, enabled);
    }

    private void handleClockRequest(Player player, ClockRequest request) throws ProtocolException {
        long received = System.currentTimeMillis();
        send(player, new ClockResponse(
                request.requestId(),
                request.clientSendTimeMillis(),
                received,
                System.currentTimeMillis()));
    }

    private void handleEditorRequest(Player player, EditorRequest request) {
        UUID playerId = player.getUniqueId();
        PlayerSession session = currentSession(player);
        if (session == null || (session.capabilities & Capabilities.SCREEN_EDITOR) == 0) {
            sendEditorResult(player, request.operationId(), false, "unsupported", "Screen editor is not negotiated.");
            return;
        }
        if (!player.hasPermission("vidscreen.admin")) {
            sendEditorResult(player, request.operationId(), false, "forbidden", "You do not have permission to edit screens.");
            return;
        }
        EditorOperation operation = new EditorOperation(session, request.operationId());
        if (editorInFlight.putIfAbsent(playerId, operation) != null) {
            sendEditorResult(player, request.operationId(), false, "busy", "Finish the current screen operation first.");
            return;
        }

        final dev.vidscreen.domain.DimensionKey actorDimension;
        try {
            actorDimension = new dev.vidscreen.domain.DimensionKey(player.getWorld().getKey().toString());
        } catch (RuntimeException error) {
            editorInFlight.remove(playerId, operation);
            sendEditorResult(player, request.operationId(), false, "invalid_dimension", "Your current dimension is unavailable.");
            return;
        }

        if (!plugin.submitMutation(
                () -> editor.apply(request, true, actorDimension),
                result -> completeEditor(operation, result),
                error -> failEditor(operation))) {
            editorInFlight.remove(playerId, operation);
            sendEditorResult(player, request.operationId(), false, "busy", "Finish the current screen operation first.");
        }
    }

    private void completeEditor(EditorOperation operation, ScreenEditorService.Result result) {
        boolean owner = editorInFlight.remove(operation.playerId, operation);
        if (result.success()) {
            try {
                switch (result.change()) {
                    case UPSERT:
                        broadcastUpsert(result.screen());
                        break;
                    case PLAYBACK:
                        broadcastPlayback(result.screen());
                        break;
                    case DELETE:
                        broadcastDelete(result.deletedScreenId(), result.revision());
                        break;
                    case NONE:
                        if (owner && isCurrent(operation.session) && synchronize(operation.session.player)) {
                            break;
                        }
                        break;
                    case SCENE:
                        synchronizeAll();
                        break;
                    default:
                        break;
                }
            } catch (RuntimeException ignored) {
                // The committed operation still receives its correlated result below.
            }
        }
        if (owner && isCurrent(operation.session)) {
            sendEditorResult(operation.session.player, result.operationId(), result.success(), result.code(), result.message());
        }
    }

    private void failEditor(EditorOperation operation) {
        if (editorInFlight.remove(operation.playerId, operation) && isCurrent(operation.session)) {
            sendEditorResult(operation.session.player, operation.operationId, false,
                    "failed", "The screen operation failed.");
        }
    }

    private void sendEditorResult(Player player, UUID operationId, boolean success, String code, String message) {
        try {
            send(player, new OperationResult(
                    operationId, success, code, message));
        } catch (ProtocolException error) {
            plugin.getLogger().log(Level.WARNING, "Failed to encode editor result", error);
        }
    }

    String describeClient(Player player) {
        PlayerSession session = currentSession(player);
        if (session == null) {
            return "Client not connected: install the matching VidScreen client mod.";
        }
        boolean editor = (session.capabilities & Capabilities.SCREEN_EDITOR) != 0;
        if ((session.capabilities & (Capabilities.MP4 | Capabilities.HLS)) == 0) {
            return "Client connected; video unavailable; screen editor "
                    + (editor ? "available." : "unavailable.");
        }
        return "Client connected; video decoder available; screen editor "
                + (editor ? "available." : "unavailable.");
    }

    boolean synchronize(Player player) {
        PlayerSession session = currentSession(player);
        if (session == null) {
            return false;
        }
        try {
            sendSnapshot(player, session.capabilities);
            return true;
        } catch (ProtocolException error) {
            plugin.getLogger().log(Level.WARNING, "Failed to synchronize player " + player.getUniqueId(), error);
            return false;
        }
    }

    void synchronizeAll() {
        for (UUID playerId : clientCapabilities.keySet()) {
            Player player = onlinePlayer(playerId);
            if (player != null) {
                synchronize(player);
            }
        }
    }

    void broadcastUpsert(ScreenState screen) {
        for (Map.Entry<UUID, Long> client : clientCapabilities.entrySet()) {
            Player player = onlinePlayer(client.getKey());
            if (player == null) {
                continue;
            }
            try {
                sendUpsert(player, client.getValue().longValue(), screen);
            } catch (ProtocolException error) {
                plugin.getLogger().log(Level.WARNING, "Failed to encode screen update", error);
            }
        }
    }

    void broadcastPlayback(ScreenState screen) {
        for (Map.Entry<UUID, Long> client : clientCapabilities.entrySet()) {
            if (!CapabilityRequirements.supportsMedia(client.getValue().longValue(), screen.media())) {
                continue;
            }
            Player player = onlinePlayer(client.getKey());
            if (player != null) {
                sendSafely(player, new PlaybackUpdate(screen.definition().id(), screen.playback()));
            }
        }
    }

    void broadcastDelete(UUID screenId, long revision) {
        broadcast(new ScreenDelete(screenId, revision));
    }

    private void broadcast(WireMessage message) {
        for (UUID playerId : clientCapabilities.keySet()) {
            Player player = onlinePlayer(playerId);
            if (player != null) {
                sendSafely(player, message);
            }
        }
    }

    private Player onlinePlayer(UUID playerId) {
        Player player = plugin.getServer().getPlayer(playerId);
        return player != null && player.isOnline() ? player : null;
    }

    private void sendSafely(Player player, WireMessage message) {
        try {
            send(player, message);
        } catch (ProtocolException error) {
            plugin.getLogger().log(Level.WARNING, "Failed to encode " + message.type(), error);
        }
    }

    private void sendSnapshot(Player player, long capabilities) throws ProtocolException {
        dev.vidscreen.protocol.message.SceneSnapshot scene = screens.scene();
        if ((capabilities & Capabilities.SCREEN_EDITOR) != 0) {
            send(player, new SceneSnapshot(scene.revision(), Collections.<ScreenState>emptyList(), scene.areas()));
            for (ScreenState screen : scene.screens()) {
                send(player, new SceneUpsert(CapabilityRequirements.visibleState(capabilities, screen)));
            }
            return;
        }
        send(player, new ScreenSnapshot(scene.revision(), Collections.<ScreenState>emptyList()));
        for (ScreenState screen : scene.screens()) {
            send(player, new ScreenUpsert(CapabilityRequirements.visibleState(capabilities, screen)));
        }
    }

    private void sendUpsert(Player player, long capabilities, ScreenState screen) throws ProtocolException {
        ScreenState visible = CapabilityRequirements.visibleState(capabilities, screen);
        if ((capabilities & Capabilities.SCREEN_EDITOR) != 0) {
            send(player, new SceneUpsert(visible));
        } else {
            send(player, new ScreenUpsert(visible));
        }
    }

    private void send(Player player, WireMessage message) throws ProtocolException {
        player.sendPluginMessage(plugin, CHANNEL, PayloadFraming.encode(codec.encode(message)));
    }

    private PlayerSession currentSession(Player player) {
        PlayerSession session = clientSessions.get(player.getUniqueId());
        return session != null && session.player == player ? session : null;
    }

    private boolean isCurrent(PlayerSession session) {
        return session.player.isOnline() && clientSessions.get(session.playerId) == session;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        PlayerSession session = clientSessions.get(playerId);
        if (session != null && session.player == event.getPlayer()) {
            clientSessions.remove(playerId, session);
            clientCapabilities.remove(playerId, session.capabilities);
        }
    }

    private static final class PlayerSession {
        private final UUID playerId;
        private final Player player;
        private final long capabilities;

        private PlayerSession(UUID playerId, Player player, long capabilities) {
            this.playerId = playerId;
            this.player = player;
            this.capabilities = capabilities;
        }
    }

    private static final class EditorOperation {
        private final PlayerSession session;
        private final UUID playerId;
        private final UUID operationId;

        private EditorOperation(PlayerSession session, UUID operationId) {
            this.session = session;
            this.playerId = session.playerId;
            this.operationId = operationId;
        }
    }
}
