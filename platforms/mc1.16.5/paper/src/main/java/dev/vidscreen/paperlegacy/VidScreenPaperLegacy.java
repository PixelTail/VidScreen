package dev.vidscreen.paperlegacy;

import java.nio.charset.StandardCharsets;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

/** Paper/Spigot 1.16.5 server adapter; no media resolution or frame relay. */
public final class VidScreenPaperLegacy extends JavaPlugin implements Listener, PluginMessageListener {
    public static final String CHANNEL = "vidscreen:control";
    private static final int MAX_PLUGIN_MESSAGE_BYTES = 32 * 1024;
    @Override
    public void onEnable() {
        getServer().getMessenger().registerOutgoingPluginChannel(this, CHANNEL);
        getServer().getMessenger().registerIncomingPluginChannel(this, CHANNEL, this);
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        getServer().getMessenger().unregisterOutgoingPluginChannel(this, CHANNEL);
        getServer().getMessenger().unregisterIncomingPluginChannel(this, CHANNEL, this);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        byte[] hello = ("VIDS" + (char) 1 + "SERVER").getBytes(StandardCharsets.US_ASCII);
        sendControl(event.getPlayer(), hello);
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!CHANNEL.equals(channel) || message == null || message.length > MAX_PLUGIN_MESSAGE_BYTES) {
            return;
        }
        // Authenticate and permission-check decoded shared protocol operations
        // before mutating state. The plugin message is control metadata only.
    }

    private void sendControl(Player player, byte[] payload) {
        if (payload == null || payload.length > MAX_PLUGIN_MESSAGE_BYTES) {
            throw new IllegalArgumentException("VidScreen plugin message exceeds the legacy channel bound");
        }
        player.sendPluginMessage(this, CHANNEL, payload);
    }
}
