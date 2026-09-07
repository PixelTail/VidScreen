package dev.vidscreen.paper;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import dev.vidscreen.domain.Facing;
import dev.vidscreen.domain.MediaDescriptor;
import dev.vidscreen.domain.PlaybackStatus;
import dev.vidscreen.domain.ScreenDefinition;
import dev.vidscreen.domain.ScreenFit;
import dev.vidscreen.domain.ScreenGeometry;
import dev.vidscreen.domain.ScreenState;
import dev.vidscreen.server.ScreenService;

final class VidScreenCommand implements CommandExecutor, TabCompleter {
    private static final List<String> SUBCOMMANDS = Arrays.asList(
            "wand", "pos1", "pos2", "create", "list", "delete", "source",
            "play", "pause", "stop", "seek", "loop", "sync");

    private final VidScreenPaperPlugin plugin;
    private final ScreenService screens;
    private final SelectionManager selections;
    private final ScreenWandListener wand;
    private final PaperScreenMessenger messenger;

    VidScreenCommand(
            VidScreenPaperPlugin plugin,
            ScreenService screens,
            SelectionManager selections,
            ScreenWandListener wand,
            PaperScreenMessenger messenger) {
        this.plugin = plugin;
        this.screens = screens;
        this.selections = selections;
        this.wand = wand;
        this.messenger = messenger;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args) {
        if (args.length == 0) {
            usage(sender);
            return true;
        }

        String subcommand = args[0].toLowerCase(Locale.ROOT);
        try {
            switch (subcommand) {
                case "wand":
                    return wand(sender);
                case "pos1":
                    return select(sender, true);
                case "pos2":
                    return select(sender, false);
                case "create":
                    return create(sender, args);
                case "list":
                    return list(sender);
                case "delete":
                    return delete(sender, args);
                case "source":
                    return source(sender, args);
                case "play":
                    return playback(sender, args, PlaybackStatus.PLAYING, null);
                case "pause":
                    return playback(sender, args, PlaybackStatus.PAUSED, null);
                case "stop":
                    return playback(sender, args, PlaybackStatus.STOPPED, 0L);
                case "seek":
                    return seek(sender, args);
                case "loop":
                    return loop(sender, args);
                case "sync":
                    return sync(sender);
                default:
                    usage(sender);
                    return true;
            }
        } catch (IllegalArgumentException error) {
            error(sender, error.getMessage());
            return true;
        }
    }

    private boolean wand(CommandSender sender) {
        Player player = requirePlayer(sender);
        requireAdmin(sender);
        player.getInventory().addItem(wand.createWand());
        success(sender, "VidScreen wand added to your inventory.");
        return true;
    }

    private boolean select(CommandSender sender, boolean first) {
        Player player = requirePlayer(sender);
        requireAdmin(sender);
        Block target = player.getTargetBlockExact(128);
        if (target == null) {
            throw new IllegalArgumentException("Look at a block within 128 blocks.");
        }
        if (first) {
            selections.setFirst(player, target);
        } else {
            selections.setSecond(player, target);
        }
        success(sender, (first ? "First" : "Second") + " corner selected at "
                + target.getX() + ", " + target.getY() + ", " + target.getZ() + ".");
        return true;
    }

    private boolean create(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        requireAdmin(sender);
        if (args.length != 3) {
            throw new IllegalArgumentException("Usage: /vidscreen create <name> <north|south|east|west|up|down>");
        }
        SelectionManager.Selection selection = selections.get(player);
        if (selection == null || !selection.complete()) {
            throw new IllegalArgumentException("Select both screen corners first.");
        }

        Facing facing = Facing.valueOf(args[2].toUpperCase(Locale.ROOT));
        ScreenGeometry geometry = ScreenGeometry.between(selection.first(), selection.second(), facing);
        ScreenDefinition definition = new ScreenDefinition(
                UUID.randomUUID(),
                args[1],
                selection.dimension(),
                geometry,
                ScreenFit.CONTAIN,
                96);

        plugin.mutate(sender,
                () -> screens.create(definition, System.currentTimeMillis()),
                created -> {
                    messenger.broadcastUpsert(created);
                    success(sender, "Created screen " + created.definition().name() + " ("
                            + geometry.widthBlocks() + "x" + geometry.heightBlocks() + ").");
                });
        return true;
    }

    private boolean list(CommandSender sender) {
        if (!sender.hasPermission("vidscreen.use")) {
            throw new IllegalArgumentException("You do not have permission to use VidScreen.");
        }
        if (screens.snapshot().isEmpty()) {
            sender.sendMessage(Component.text("No VidScreen screens exist.", NamedTextColor.GRAY));
            return true;
        }
        sender.sendMessage(Component.text("VidScreen screens:", NamedTextColor.AQUA));
        for (ScreenState screen : screens.snapshot()) {
            sender.sendMessage(Component.text(
                    "- " + screen.definition().name()
                            + " [" + screen.playback().status().name().toLowerCase(Locale.ROOT) + "] "
                            + screen.definition().dimension().value(),
                    NamedTextColor.GRAY));
        }
        return true;
    }

    private boolean delete(CommandSender sender, String[] args) {
        requireAdmin(sender);
        ScreenState screen = requireNamedScreen(args, 2, "Usage: /vidscreen delete <name>");
        plugin.mutate(sender,
                () -> screens.delete(screen.definition().id()),
                deleted -> {
                    messenger.broadcastDelete(deleted.definition().id(), screens.revision());
                    success(sender, "Deleted screen " + deleted.definition().name() + ".");
                });
        return true;
    }

    private boolean source(CommandSender sender, String[] args) {
        requireAdmin(sender);
        if (args.length < 3 || args.length > 4) {
            throw new IllegalArgumentException("Usage: /vidscreen source <name> <https-url> [resolver]");
        }
        ScreenState screen = requireNamedScreen(args, 3, "Usage: /vidscreen source <name> <https-url> [resolver]");
        URI source = URI.create(args[2]);
        validateSource(source);
        String resolver = args.length == 4 ? args[3].toLowerCase(Locale.ROOT) : inferResolver(source);
        validateResolverSource(resolver, source);
        MediaDescriptor media = new MediaDescriptor(resolver, source.toASCIIString());

        plugin.mutate(sender,
                () -> screens.setMedia(screen.definition().id(), media, System.currentTimeMillis()),
                changed -> {
                    messenger.broadcastUpsert(changed);
                    success(sender, "Updated source for " + changed.definition().name() + ".");
                });
        return true;
    }

    private boolean playback(CommandSender sender, String[] args, PlaybackStatus status, Long fixedPosition) {
        requireAdmin(sender);
        ScreenState screen = requireNamedScreen(args, 2, "Usage: /vidscreen "
                + status.name().toLowerCase(Locale.ROOT) + " <name>");
        long now = System.currentTimeMillis();
        long position = fixedPosition != null ? fixedPosition : screen.playback().targetPositionMillis(now);
        plugin.mutate(sender,
                () -> screens.setPlayback(screen.definition().id(), status, position, now),
                changed -> {
                    messenger.broadcastPlayback(changed);
                    success(sender, status.name().toLowerCase(Locale.ROOT) + " " + changed.definition().name() + ".");
                });
        return true;
    }

    private boolean seek(CommandSender sender, String[] args) {
        requireAdmin(sender);
        if (args.length != 3) {
            throw new IllegalArgumentException("Usage: /vidscreen seek <name> <milliseconds>");
        }
        ScreenState screen = requireNamedScreen(args, 3, "Usage: /vidscreen seek <name> <milliseconds>");
        long position = Long.parseLong(args[2]);
        if (position < 0) {
            throw new IllegalArgumentException("Seek position must be non-negative.");
        }
        long now = System.currentTimeMillis();
        plugin.mutate(sender,
                () -> screens.setPlayback(screen.definition().id(), screen.playback().status(), position, now),
                changed -> {
                    messenger.broadcastPlayback(changed);
                    success(sender, "Seeked " + changed.definition().name() + " to " + position + " ms.");
                });
        return true;
    }

    private boolean loop(CommandSender sender, String[] args) {
        requireAdmin(sender);
        if (args.length != 3 || !("true".equalsIgnoreCase(args[2]) || "false".equalsIgnoreCase(args[2]))) {
            throw new IllegalArgumentException("Usage: /vidscreen loop <name> <true|false>");
        }
        ScreenState screen = requireNamedScreen(args, 3, "Usage: /vidscreen loop <name> <true|false>");
        boolean looping = Boolean.parseBoolean(args[2]);
        long now = System.currentTimeMillis();
        plugin.mutate(sender,
                () -> screens.setRateAndLoop(
                        screen.definition().id(), screen.playback().playbackRate(), looping, now),
                changed -> {
                    messenger.broadcastPlayback(changed);
                    success(sender, "Looping " + (looping ? "enabled" : "disabled") + " for "
                            + changed.definition().name() + ".");
                });
        return true;
    }

    private boolean sync(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (!sender.hasPermission("vidscreen.use")) {
            throw new IllegalArgumentException("You do not have permission to use VidScreen.");
        }
        if (messenger.synchronize(player)) {
            success(player, "Sent a full authoritative screen snapshot.");
        } else {
            throw new IllegalArgumentException("No compatible VidScreen client handshake is active.");
        }
        return true;
    }

    private ScreenState requireNamedScreen(String[] args, int expectedMinimum, String usage) {
        if (args.length < expectedMinimum) {
            throw new IllegalArgumentException(usage);
        }
        ScreenState screen = screens.findByName(args[1]);
        if (screen == null) {
            throw new IllegalArgumentException("Unknown screen: " + args[1]);
        }
        return screen;
    }

    private static void validateSource(URI source) {
        if (!source.isAbsolute() || !"https".equalsIgnoreCase(source.getScheme()) || source.getHost() == null) {
            throw new IllegalArgumentException("Media source must be an absolute HTTPS URL.");
        }
        if (source.getRawUserInfo() != null || source.getRawFragment() != null) {
            throw new IllegalArgumentException("Media source cannot contain credentials or a fragment.");
        }
        if (source.getPort() != -1 && source.getPort() != 443) {
            throw new IllegalArgumentException("Media source must use the default HTTPS port.");
        }
    }

    private static String inferResolver(URI source) {
        String host = source.getHost().toLowerCase(Locale.ROOT);
        if (providerHost(host, "bilibili.com") || providerHost(host, "b23.tv")) {
            return "bilibili";
        }
        if (providerHost(host, "youtube.com") || providerHost(host, "youtu.be")) {
            return "youtube";
        }
        if (providerHost(host, "twitch.tv")) {
            return "twitch";
        }
        return "direct";
    }

    private static void validateResolverSource(String resolver, URI source) {
        String host = source.getHost().toLowerCase(Locale.ROOT);
        String path = source.getPath().toLowerCase(Locale.ROOT);
        boolean valid;
        switch (resolver) {
            case "direct":
                valid = path.endsWith(".mp4") || path.endsWith(".m3u8");
                break;
            case "bilibili":
                valid = providerHost(host, "bilibili.com") || providerHost(host, "b23.tv");
                break;
            case "youtube":
                valid = providerHost(host, "youtube.com") || providerHost(host, "youtu.be");
                break;
            case "twitch":
                valid = providerHost(host, "twitch.tv");
                break;
            default:
                throw new IllegalArgumentException("Unknown media resolver: " + resolver);
        }
        if (!valid) {
            throw new IllegalArgumentException("Media URL does not match resolver " + resolver + ".");
        }
    }

    private static boolean providerHost(String host, String suffix) {
        return host.equals(suffix) || host.endsWith("." + suffix);
    }

    private static Player requirePlayer(CommandSender sender) {
        if (!(sender instanceof Player)) {
            throw new IllegalArgumentException("This command must be run by a player.");
        }
        return (Player) sender;
    }

    private static void requireAdmin(CommandSender sender) {
        if (!sender.hasPermission("vidscreen.admin")) {
            throw new IllegalArgumentException("You do not have permission to modify VidScreen screens.");
        }
    }

    private static void usage(CommandSender sender) {
        sender.sendMessage(Component.text(
                "/vidscreen <" + String.join("|", SUBCOMMANDS) + ">",
                NamedTextColor.AQUA));
    }

    private static void success(CommandSender sender, String message) {
        sender.sendMessage(Component.text(message, NamedTextColor.GREEN));
    }

    private static void error(CommandSender sender, String message) {
        sender.sendMessage(Component.text(message == null ? "VidScreen operation failed." : message, NamedTextColor.RED));
    }

    @Override
    public @Nullable List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String[] args) {
        if (args.length == 1) {
            return filter(SUBCOMMANDS, args[0]);
        }
        if (args.length == 2 && Arrays.asList("delete", "source", "play", "pause", "stop", "seek", "loop")
                .contains(args[0].toLowerCase(Locale.ROOT))) {
            List<String> names = new ArrayList<>();
            for (ScreenState screen : screens.snapshot()) {
                names.add(screen.definition().name());
            }
            return filter(names, args[1]);
        }
        if (args.length == 3 && "create".equalsIgnoreCase(args[0])) {
            return filter(Arrays.asList("north", "south", "east", "west", "up", "down"), args[2]);
        }
        if (args.length == 3 && "loop".equalsIgnoreCase(args[0])) {
            return filter(Arrays.asList("true", "false"), args[2]);
        }
        if (args.length == 4 && "source".equalsIgnoreCase(args[0])) {
            return filter(Arrays.asList("direct", "bilibili", "youtube", "twitch"), args[3]);
        }
        return Collections.emptyList();
    }

    private static List<String> filter(List<String> values, String prefix) {
        String normalized = prefix.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String value : values) {
            if (value.toLowerCase(Locale.ROOT).startsWith(normalized)) {
                result.add(value);
            }
        }
        return result;
    }
}
