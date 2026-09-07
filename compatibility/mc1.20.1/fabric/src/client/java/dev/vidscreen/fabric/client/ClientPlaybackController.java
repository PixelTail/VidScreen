package dev.vidscreen.fabric.client;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;

import net.minecraft.client.MinecraftClient;

import dev.vidscreen.client.MediaPlayerFactory;
import dev.vidscreen.client.MediaResolverRegistry;
import dev.vidscreen.client.PlaybackFailure;
import dev.vidscreen.client.ScreenPlaybackCoordinator;
import dev.vidscreen.client.VisibleScreenSelector;
import dev.vidscreen.domain.ScreenState;
import dev.vidscreen.media.DirectMediaResolver;
import dev.vidscreen.media.MediaResolver;
import dev.vidscreen.media.MediaUrlPolicy;
import dev.vidscreen.media.ffmpeg.FfmpegExecutables;
import dev.vidscreen.media.ffmpeg.FfmpegVideoPlayer;
import dev.vidscreen.media.ytdlp.YtDlpExecutable;
import dev.vidscreen.media.ytdlp.YtDlpMediaResolver;
import dev.vidscreen.protocol.Capabilities;

final class ClientPlaybackController implements AutoCloseable {
    private static final int VIDEO_WIDTH = 640;
    private static final int VIDEO_HEIGHT = 360;

    private final ClientScreenStore store;
    private final ScreenTextureManager textures = new ScreenTextureManager();
    private final VisibleScreenSelector selector = new VisibleScreenSelector(maximumActiveScreens());
    private final ScreenPlaybackCoordinator playback;
    private final long capabilities;

    ClientPlaybackController(ClientScreenStore store, Logger logger) {
        this.store = store;
        Optional<FfmpegExecutables> discovered = FfmpegExecutables.discover();
        if (!discovered.isPresent()) {
            playback = null;
            capabilities = 0;
            logger.warn("VidScreen playback is disabled: install ffmpeg/ffprobe for the 1.20.1 experimental lane");
            return;
        }
        FfmpegExecutables executables = discovered.get();
        MediaPlayerFactory playerFactory = (width, height) -> new FfmpegVideoPlayer(
                executables.ffmpeg(), executables.ffprobe(), width, height);
        MediaUrlPolicy urlPolicy = MediaUrlPolicy.strictPublicHttps();
        List<MediaResolver> availableResolvers = new ArrayList<MediaResolver>();
        availableResolvers.add(new DirectMediaResolver(urlPolicy));
        long availableCapabilities = Capabilities.MP4 | Capabilities.HLS;
        Optional<Path> ytDlp = YtDlpExecutable.discover();
        if (ytDlp.isPresent()) {
            availableResolvers.add(YtDlpMediaResolver.bilibili(ytDlp.get(), urlPolicy));
            availableResolvers.add(YtDlpMediaResolver.youtube(ytDlp.get(), urlPolicy));
            availableResolvers.add(YtDlpMediaResolver.twitch(ytDlp.get(), urlPolicy));
            availableCapabilities |= Capabilities.BILIBILI | Capabilities.YOUTUBE
                    | Capabilities.TWITCH | Capabilities.LIVE_STREAMS;
        }
        playback = new ScreenPlaybackCoordinator(
                new MediaResolverRegistry(availableResolvers), playerFactory,
                failure -> logFailure(logger, failure), VIDEO_WIDTH, VIDEO_HEIGHT);
        capabilities = availableCapabilities;
        logger.info("VidScreen 1.20.1 client playback enabled with external ffmpeg");
    }

    long capabilities() {
        return capabilities;
    }

    ScreenTextureManager textures() {
        return textures;
    }

    void tick(long estimatedServerTimeMillis) {
        if (playback == null) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) {
            clear();
            return;
        }
        List<ScreenState> visible = new ArrayList<ScreenState>(selector.select(
                store.snapshot(), client.world.getRegistryKey().getValue().toString(),
                client.player.getX(), client.player.getY(), client.player.getZ()));
        visible.removeIf(screen -> !isAnchorChunkLoaded(client, screen));
        playback.reconcile(visible, estimatedServerTimeMillis);
        textures.update(visible, playback);
    }

    void clear() {
        if (playback != null) {
            playback.reconcile(Collections.<ScreenState>emptyList(), System.currentTimeMillis());
        }
        textures.clear();
    }

    private static boolean isAnchorChunkLoaded(MinecraftClient client, ScreenState screen) {
        return client.world != null && client.world.isChunkLoaded(
                VisibleScreenSelector.anchorChunkX(screen), VisibleScreenSelector.anchorChunkZ(screen));
    }

    private static int maximumActiveScreens() {
        try {
            return Math.max(1, Math.min(16,
                    Integer.parseInt(System.getProperty("vidscreen.maxActiveScreens", "4"))));
        } catch (NumberFormatException ignored) {
            return 4;
        }
    }

    private static void logFailure(Logger logger, PlaybackFailure failure) {
        logger.warn("VidScreen playback failed for screen {} during {} ({})",
                failure.screenId(), failure.stage(), failure.cause().getClass().getSimpleName());
    }

    @Override
    public void close() {
        textures.close();
        if (playback != null) {
            playback.close();
        }
    }
}
