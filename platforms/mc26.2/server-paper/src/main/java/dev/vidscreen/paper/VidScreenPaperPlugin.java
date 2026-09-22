package dev.vidscreen.paper;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
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
    private final AtomicBoolean acceptingMutations = new AtomicBoolean(false);
    private final AtomicLong mutationGeneration = new AtomicLong();
    private volatile ThreadPoolExecutor mutationExecutor;

    @Override
    public void onEnable() {
        ThreadPoolExecutor previous = mutationExecutor;
        if (previous != null && !previous.isTerminated()) {
            getLogger().severe("VidScreen cannot enable while a previous mutation worker is still stopping.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        Path screenFile = getDataFolder().toPath().resolve("screens.bin");
        screens = new ScreenService(new FileScreenRepository(screenFile));
        try {
            screens.load();
        } catch (IOException error) {
            getLogger().log(Level.SEVERE, "Could not load VidScreen screens", error);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        startMutationExecutor();

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
        stopMutationExecutor();
        getServer().getMessenger().unregisterIncomingPluginChannel(this);
        getServer().getMessenger().unregisterOutgoingPluginChannel(this);
    }

    <T> void mutate(CommandSender sender, CheckedSupplier<T> operation, Consumer<T> success) {
        if (submitMutation(
                operation,
                success,
                error -> sender.sendMessage(Component.text(
                        error.getMessage() == null ? "VidScreen operation failed." : error.getMessage(),
                        NamedTextColor.RED)))) {
            return;
        }
        if (acceptingMutations.get()) {
            sender.sendMessage(Component.text("VidScreen is busy; try again shortly.", NamedTextColor.YELLOW));
        }
    }

    <T> boolean submitMutation(
            CheckedSupplier<T> operation,
            Consumer<T> success,
            Consumer<Exception> failure) {
        ThreadPoolExecutor executor = mutationExecutor;
        if (!acceptingMutations.get() || executor == null) {
            return false;
        }
        long generation = mutationGeneration.get();
        java.util.concurrent.FutureTask<Void> task = new java.util.concurrent.FutureTask<Void>(() -> {
            if (!acceptingMutations.get() || mutationGeneration.get() != generation) {
                return null;
            }
            try {
                T result = operation.get();
                dispatchMain(generation, () -> success.accept(result));
            } catch (Exception error) {
                dispatchMain(generation, () -> failure.accept(error));
            }
            return null;
        });
        try {
            executor.execute(task);
            return true;
        } catch (RejectedExecutionException error) {
            return false;
        }
    }

    private void dispatchMain(long generation, Runnable callback) {
        if (!acceptingMutations.get() || mutationGeneration.get() != generation) {
            return;
        }
        try {
            getServer().getScheduler().runTask(this, () -> {
                if (acceptingMutations.get() && mutationGeneration.get() == generation) {
                    callback.run();
                }
            });
        } catch (RuntimeException ignored) {
            // Shutdown can reject callbacks after the mutation has completed.
        }
    }

    private void startMutationExecutor() {
        mutationGeneration.incrementAndGet();
        AtomicInteger threadId = new AtomicInteger();
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable,
                    "vidscreen-" + getDescription().getName().toLowerCase(java.util.Locale.ROOT)
                            + "-mutation-" + threadId.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        mutationExecutor = new ThreadPoolExecutor(
                1,
                1,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<Runnable>(64),
                factory,
                new ThreadPoolExecutor.AbortPolicy());
        acceptingMutations.set(true);
    }

    private void stopMutationExecutor() {
        acceptingMutations.set(false);
        ThreadPoolExecutor executor = mutationExecutor;
        if (executor == null) {
            return;
        }
        for (Runnable queued : executor.getQueue().toArray(new Runnable[0])) {
            if (queued instanceof Future<?>) {
                ((Future<?>) queued).cancel(false);
            }
            executor.remove(queued);
        }
        executor.shutdown();
        boolean terminated = awaitTermination(executor, 2L, TimeUnit.SECONDS);
        if (!terminated) {
            List<Runnable> abandoned = executor.shutdownNow();
            for (Runnable queued : abandoned) {
                if (queued instanceof Future<?>) {
                    ((Future<?>) queued).cancel(true);
                }
            }
            terminated = awaitTermination(executor, 500L, TimeUnit.MILLISECONDS);
        }
        if (terminated) {
            mutationExecutor = null;
        } else {
            getLogger().warning(
                    "VidScreen mutation worker did not stop; an active persistence operation may still be writing.");
        }
    }

    private static boolean awaitTermination(ThreadPoolExecutor executor, long timeout, TimeUnit unit) {
        try {
            return executor.awaitTermination(timeout, unit);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    @FunctionalInterface
    interface CheckedSupplier<T> {
        T get() throws Exception;
    }
}
