package dev.vidscreen.legacy.paper;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Server-only Paper feasibility adapter. It exposes no client/native classes
 * and deliberately limits itself to a capability/status command until exact
 * legacy lifecycle tests are available.
 */
public final class PaperLegacyPlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        if (getCommand("vidscreen") != null) {
            getCommand("vidscreen").setExecutor(this::handleCommand);
        }
    }

    private boolean handleCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 && "status".equalsIgnoreCase(args[0])) {
            sender.sendMessage("VidScreen 1.19.2 lane is experimental; runtime support is not claimed.");
            return true;
        }
        sender.sendMessage("Usage: /vidscreen status");
        return true;
    }
}
