package dev.vidscreen.neoforge.client;

import dev.vidscreen.client.ClientScreenStore;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;

import net.minecraft.client.Minecraft;

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
import dev.vidscreen.media.ffmpeg.nativeapi.NativeFfmpegRuntime;
import dev.vidscreen.media.ffmpeg.nativeapi.NativeFfmpegVideoPlayer;
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
        MediaPlayerFactory playerFactory;
        boolean usingNativeRuntime = nativeRuntimeAvailable();
        if (usingNativeRuntime) {
            playerFactory = NativeFfmpegVideoPlayer::new;
        } else {
            Optional<FfmpegExecutables> discovered = FfmpegExecutables.discover();
            if (!discovered.isPresent()) {
                playback = null;
                capabilities = 0;
                logger.warn("VidScreen video playback is disabled: install the VidScreen NeoForge media runtime. "
                        + "The external ffmpeg/ffprobe adapter remains available as a development fallback");
                return;
            }
            FfmpegExecutables executables = discovered.get();
            playerFactory = (width, height) -> new FfmpegVideoPlayer(
                    executables.ffmpeg(), executables.ffprobe(), width, height);
        }

        MediaUrlPolicy urlPolicy = MediaUrlPolicy.strictPublicHttps();
        List<MediaResolver> availableResolvers = new ArrayList<>();
        availableResolvers.add(new DirectMediaResolver(urlPolicy));
        long availableCapabilities = Capabilities.MP4 | Capabilities.HLS;

        Optional<Path> ytDlp = YtDlpExecutable.discover();
        if (ytDlp.isPresent()) {
            availableResolvers.add(YtDlpMediaResolver.bilibili(ytDlp.get(), urlPolicy));
            availableResolvers.add(YtDlpMediaResolver.youtube(ytDlp.get(), urlPolicy));
            availableResolvers.add(YtDlpMediaResolver.twitch(ytDlp.get(), urlPolicy));
            availableCapabilities |= Capabilities.BILIBILI
                    | Capabilities.YOUTUBE
                    | Capabilities.TWITCH
                    | Capabilities.LIVE_STREAMS;
        }

        MediaResolverRegistry resolvers = new MediaResolverRegistry(availableResolvers);
        playback = new ScreenPlaybackCoordinator(
                resolvers,
                playerFactory,
                failure -> {
                    logFailure(logger, failure);
                    Minecraft.getInstance().execute(() -> {
                        Minecraft client = Minecraft.getInstance();
                        if (client.player == null) { return; }
                        for (ScreenState screen : store.snapshot()) {
                            if (screen.definition().id().equals(failure.screenId())) {
                                dev.vidscreen.gui.EditorClient.message(net.minecraft.network.chat.Component.literal(
                                        "VidScreen: " + screen.definition().name()
                                        + " 播放失败（" + failure.stage() + "）。请检查视频链接或媒体运行库，再点击继续重试。"), false);
                                break;
                            }
                        }
                    });
                },
                VIDEO_WIDTH,
                VIDEO_HEIGHT);
        capabilities = availableCapabilities;
        if (usingNativeRuntime) {
            logger.info("VidScreen direct MP4/HLS playback is enabled through the bundled native media runtime");
        } else {
            logger.info("VidScreen direct MP4/HLS playback is enabled through the external-process fallback");
        }
        if (ytDlp.isPresent()) {
            logger.info("VidScreen Bilibili, YouTube, and Twitch resolution is enabled through yt-dlp");
        } else {
            logger.info("VidScreen provider resolution is disabled; direct MP4/HLS playback remains available");
        }
    }

    String availability() {
        return playback == null
                ? "无法播放视频：请安装匹配的 VidScreen 媒体运行库。"
                : "视频已就绪。按 N 打开观影菜单，按 U 调整屏幕。";
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
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) {
            clear();
            return;
        }
        List<ScreenState> screens = new ArrayList<>(selector.select(
                store.playbackSnapshot(client.level.dimension().identifier().toString(), client.player.getX(), client.player.getY(), client.player.getZ()),
                client.level.dimension().identifier().toString(),
                client.player.getX(),
                client.player.getY(),
                client.player.getZ()));
        screens.removeIf(screen -> !isAnchorChunkLoaded(client, screen));
        playback.reconcile(screens, estimatedServerTimeMillis);
        textures.update(screens, playback);
    }

    void clear() {
        if (playback != null) {
            playback.reconcile(Collections.<ScreenState>emptyList(), System.currentTimeMillis());
        }
        textures.clear();
    }

    private static boolean isAnchorChunkLoaded(Minecraft client, ScreenState screen) {
        return client.level != null
                && client.level.getChunkSource().hasChunk(
                        VisibleScreenSelector.anchorChunkX(screen),
                        VisibleScreenSelector.anchorChunkZ(screen));
    }

    private static boolean nativeRuntimeAvailable() {
        try {
            return NativeFfmpegRuntime.isAvailable();
        } catch (LinkageError error) {
            return false;
        } catch (RuntimeException error) {
            return false;
        }
    }

    private static int maximumActiveScreens() {
        try {
            int configured = Integer.parseInt(System.getProperty("vidscreen.maxActiveScreens", "4"));
            return Math.max(1, Math.min(16, configured));
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
