package dev.vidscreen.fabric.client;

import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.util.Identifier;

import dev.vidscreen.client.ScreenPlaybackCoordinator;
import dev.vidscreen.domain.MediaDescriptor;
import dev.vidscreen.domain.ScreenState;
import dev.vidscreen.media.LatestFrameQueue;
import dev.vidscreen.media.PixelFormat;
import dev.vidscreen.media.VideoFrame;

final class ScreenTextureManager implements AutoCloseable {
    private final Map<UUID, Entry> entries = new LinkedHashMap<>();
    private boolean closed;

    void update(Collection<ScreenState> screens, ScreenPlaybackCoordinator playback) {
        if (closed) {
            return;
        }
        Set<UUID> active = new HashSet<>();
        for (ScreenState screen : screens) {
            UUID screenId = screen.definition().id();
            MediaDescriptor media = screen.media();
            if (media == null) {
                continue;
            }
            LatestFrameQueue frames = playback.frameQueue(screenId);
            if (frames == null) {
                continue;
            }
            active.add(screenId);
            Entry current = entries.get(screenId);
            if (current != null && !current.media().equals(media)) {
                remove(screenId);
                current = null;
            }
            VideoFrame frame = frames.poll();
            if (frame == null) {
                continue;
            }
            try {
                if (current == null || current.width() != frame.width() || current.height() != frame.height()) {
                    remove(screenId);
                    current = create(screenId, media, frame.width(), frame.height());
                    entries.put(screenId, current);
                }
                copy(frame, current.image());
                current.texture().upload();
            } finally {
                frame.close();
            }
        }

        UUID[] existing = entries.keySet().toArray(UUID[]::new);
        for (UUID screenId : existing) {
            if (!active.contains(screenId)) {
                remove(screenId);
            }
        }
    }

    Identifier texture(UUID screenId) {
        Entry entry = entries.get(screenId);
        return entry == null ? null : entry.location();
    }

    void clear() {
        UUID[] screenIds = entries.keySet().toArray(UUID[]::new);
        for (UUID screenId : screenIds) {
            remove(screenId);
        }
    }

    private static Entry create(UUID screenId, MediaDescriptor media, int width, int height) {
        Identifier location = Identifier.of("vidscreen", "screen/" + screenId.toString().replace("-", ""));
        NativeImage image = new NativeImage(NativeImage.Format.RGBA, width, height, false);
        NativeImageBackedTexture texture = new NativeImageBackedTexture(image);
        MinecraftClient.getInstance().getTextureManager().registerTexture(location, texture);
        return new Entry(location, media, texture, image, width, height);
    }

    private static void copy(VideoFrame frame, NativeImage image) {
        if (image == null || image.getWidth() != frame.width() || image.getHeight() != frame.height()) {
            throw new IllegalArgumentException("Video frame dimensions do not match the screen texture");
        }
        if (frame.format() != PixelFormat.RGBA8 && frame.format() != PixelFormat.BGRA8) {
            throw new IllegalArgumentException("Unsupported video pixel format: " + frame.format());
        }
        ByteBuffer source = frame.pixels();
        int sourceBase = source.position();
        for (int y = 0; y < frame.height(); y++) {
            int rowStart = sourceBase + y * frame.rowStride();
            for (int x = 0; x < frame.width(); x++) {
                int pixel = rowStart + x * 4;
                int r;
                int g;
                int b;
                if (frame.format() == PixelFormat.RGBA8) {
                    r = source.get(pixel) & 0xFF;
                    g = source.get(pixel + 1) & 0xFF;
                    b = source.get(pixel + 2) & 0xFF;
                } else {
                    b = source.get(pixel) & 0xFF;
                    g = source.get(pixel + 1) & 0xFF;
                    r = source.get(pixel + 2) & 0xFF;
                }
                int a = source.get(pixel + 3) & 0xFF;
                image.setColor(x, y, r | (g << 8) | (b << 16) | (a << 24));
            }
        }
    }

    private void remove(UUID screenId) {
        Entry removed = entries.remove(screenId);
        if (removed != null) {
            TextureManager textures = MinecraftClient.getInstance().getTextureManager();
            textures.destroyTexture(removed.location());
        }
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            clear();
        }
    }

    private record Entry(
            Identifier location,
            MediaDescriptor media,
            NativeImageBackedTexture texture,
            NativeImage image,
            int width,
            int height) {
    }
}
