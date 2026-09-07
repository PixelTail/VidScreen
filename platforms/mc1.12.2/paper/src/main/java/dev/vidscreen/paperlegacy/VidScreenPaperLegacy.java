package dev.vidscreen.paperlegacy;

import java.util.Arrays;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.plugin.java.JavaPlugin;

/** 1.12.2 server-only adapter; it never resolves or relays media. */
public final class VidScreenPaperLegacy extends JavaPlugin
        implements Listener, PluginMessageListener {
    public static final String CHANNEL = "VIDSCREEN";
    private static final int MAX_PLUGIN_MESSAGE_BYTES = 16 * 1024;

    @Override
    public void onEnable() {
        getServer().getMessenger().registerOutgoingPluginChannel(this, CHANNEL);
        getServer().getMessenger().registerIncomingPluginChannel(this, CHANNEL, this);
        getServer().getPluginManager().registerEvents(this, this);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        byte[] hello = new byte[] {'V', 'I', 'D', 'S', 1, 'S', 'E', 'R', 'V', 'E', 'R'};
        sendControl(event.getPlayer(), hello);
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!CHANNEL.equals(channel) || message == null
                || message.length > MAX_PLUGIN_MESSAGE_BYTES) {
            return;
        }
        // The legacy channel carries bounded control metadata only. Never
        // resolve URLs, accept frame bytes, or perform blocking work here.
    }

    public void sendControl(Player player, byte[] payload) {
        if (player == null || payload == null || payload.length > MAX_PLUGIN_MESSAGE_BYTES) {
            throw new IllegalArgumentException("Invalid VidScreen plugin payload");
        }
        player.sendPluginMessage(this, CHANNEL, Arrays.copyOf(payload, payload.length));
    }
}
