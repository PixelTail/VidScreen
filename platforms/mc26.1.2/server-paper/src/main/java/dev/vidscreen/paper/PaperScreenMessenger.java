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
import dev.vidscreen.protocol.ProtocolVersion;
import dev.vidscreen.protocol.WireCodec;
import dev.vidscreen.protocol.WireMessage;
import dev.vidscreen.protocol.message.ClientHello;
import dev.vidscreen.protocol.message.ClockRequest;
import dev.vidscreen.protocol.message.ClockResponse;
import dev.vidscreen.protocol.message.PlaybackUpdate;
import dev.vidscreen.protocol.message.ScreenDelete;
import dev.vidscreen.protocol.message.ScreenSnapshot;
import dev.vidscreen.protocol.message.ScreenUpsert;
import dev.vidscreen.protocol.message.ServerHello;
import dev.vidscreen.server.ScreenService;

final class PaperScreenMessenger implements PluginMessageListener, Listener {
    static final String CHANNEL = "vidscreen:main";
    private static final long SERVER_CAPABILITIES = Capabilities.MP4
            | Capabilities.HLS
            | Capabilities.BILIBILI
            | Capabilities.YOUTUBE
            | Capabilities.TWITCH
            | Capabilities.LIVE_STREAMS
            | Capabilities.SPATIAL_AUDIO;

    private final VidScreenPaperPlugin plugin;
    private final ScreenService screens;
    private final WireCodec codec = new WireCodec();
    private final Map<UUID, Long> clientCapabilities = new ConcurrentHashMap<UUID, Long>();

    PaperScreenMessenger(VidScreenPaperPlugin plugin, ScreenService screens) {
        this.plugin = plugin;
        this.screens = screens;
    }

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, @NotNull byte[] payload) {
        if (!CHANNEL.equals(channel)) {
            return;
        }

        try {
            WireMessage message = codec.decode(payload);
            if (message instanceof ClientHello) {
                handleHello(player, (ClientHello) message);
            } else if (message instanceof ClockRequest && clientCapabilities.containsKey(player.getUniqueId())) {
                handleClockRequest(player, (ClockRequest) message);
            }
        } catch (ProtocolException | RuntimeException error) {
            plugin.getLogger().log(Level.WARNING,
                    "Rejected VidScreen payload from " + player.getUniqueId() + ": " + error.getMessage());
        }
    }

    private void handleHello(Player player, ClientHello hello) throws ProtocolException {
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
            clientCapabilities.remove(player.getUniqueId());
            return;
        }

        clientCapabilities.put(player.getUniqueId(), enabled);
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

    boolean synchronize(Player player) {
        Long capabilities = clientCapabilities.get(player.getUniqueId());
        if (capabilities == null) {
            return false;
        }
        try {
            sendSnapshot(player, capabilities.longValue());
            return true;
        } catch (ProtocolException error) {
            plugin.getLogger().log(Level.WARNING, "Failed to synchronize player " + player.getUniqueId(), error);
            return false;
        }
    }

    void broadcastUpsert(ScreenState screen) {
        for (Map.Entry<UUID, Long> client : clientCapabilities.entrySet()) {
            Player player = onlinePlayer(client.getKey());
            if (player == null) {
                continue;
            }
            try {
                if (CapabilityRequirements.supportsMedia(client.getValue().longValue(), screen.media())) {
                    send(player, new ScreenUpsert(screen));
                } else {
                    send(player, new ScreenDelete(screen.definition().id(), screen.revision()));
                }
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
        send(player, new ScreenSnapshot(screens.revision(), Collections.<ScreenState>emptyList()));
        for (ScreenState screen : screens.snapshot()) {
            if (CapabilityRequirements.supportsMedia(capabilities, screen.media())) {
                send(player, new ScreenUpsert(screen));
            }
        }
    }

    private void send(Player player, WireMessage message) throws ProtocolException {
        player.sendPluginMessage(plugin, CHANNEL, codec.encode(message));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        clientCapabilities.remove(event.getPlayer().getUniqueId());
    }
}
