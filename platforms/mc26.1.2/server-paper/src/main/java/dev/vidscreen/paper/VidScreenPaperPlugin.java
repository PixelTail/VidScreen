package dev.vidscreen.paper;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.logging.Level;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import dev.vidscreen.server.FileScreenRepository;
import dev.vidscreen.server.ScreenService;

public final class VidScreenPaperPlugin extends JavaPlugin {
    private ScreenService screens;
    private PaperScreenMessenger messenger;

    @Override
    public void onEnable() {
        Path screenFile = getDataFolder().toPath().resolve("screens.bin");
        screens = new ScreenService(new FileScreenRepository(screenFile));
        try {
            screens.load();
        } catch (IOException error) {
            getLogger().log(Level.SEVERE, "Could not load VidScreen screens", error);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        SelectionManager selections = new SelectionManager(this);
        ScreenWandListener wand = new ScreenWandListener(selections);
        messenger = new PaperScreenMessenger(this, screens);
        VidScreenCommand commandHandler = new VidScreenCommand(this, screens, selections, wand, messenger);

        getServer().getMessenger().registerIncomingPluginChannel(this, PaperScreenMessenger.CHANNEL, messenger);
        getServer().getMessenger().registerOutgoingPluginChannel(this, PaperScreenMessenger.CHANNEL);
        getServer().getPluginManager().registerEvents(wand, this);
        getServer().getPluginManager().registerEvents(messenger, this);

        PluginCommand command = Objects.requireNonNull(getCommand("vidscreen"), "vidscreen command");
        command.setExecutor(commandHandler);
        command.setTabCompleter(commandHandler);

        getLogger().info("VidScreen enabled with " + screens.snapshot().size() + " persisted screens.");
    }

    @Override
    public void onDisable() {
        getServer().getMessenger().unregisterIncomingPluginChannel(this);
        getServer().getMessenger().unregisterOutgoingPluginChannel(this);
    }

    <T> void mutate(CommandSender sender, CheckedSupplier<T> operation, Consumer<T> success) {
        getServer().getScheduler().runTaskAsynchronously(this, () -> {
            try {
                T result = operation.get();
                getServer().getScheduler().runTask(this, () -> success.accept(result));
            } catch (Exception error) {
                getLogger().log(Level.WARNING, "VidScreen mutation failed", error);
                getServer().getScheduler().runTask(this, () -> sender.sendMessage(Component.text(
                        error.getMessage() == null ? "VidScreen operation failed." : error.getMessage(),
                        NamedTextColor.RED)));
            }
        });
    }

    @FunctionalInterface
    interface CheckedSupplier<T> {
        T get() throws Exception;
    }
}
