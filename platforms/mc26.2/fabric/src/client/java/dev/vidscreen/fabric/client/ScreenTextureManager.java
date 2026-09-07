package dev.vidscreen.fabric.client;

import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;

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
                copy(frame, current.texture().getPixels());
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
        Identifier location = Identifier.fromNamespaceAndPath(
                "vidscreen", "screen/" + screenId.toString().replace("-", ""));
        DynamicTexture texture = new DynamicTexture("VidScreen " + screenId, width, height, false);
        Minecraft.getInstance().getTextureManager().register(location, texture);
        return new Entry(location, media, texture, width, height);
    }

    private static void copy(VideoFrame frame, NativeImage image) {
        if (image == null || image.getWidth() != frame.width() || image.getHeight() != frame.height()) {
            throw new IllegalArgumentException("Video frame dimensions do not match the screen texture");
        }
        ByteBuffer source = frame.pixels();
        ByteBuffer destination = image.getPixelBytes();
        int sourceBase = source.position();
        int rowBytes = frame.width() * 4;
        if (frame.format() == PixelFormat.RGBA8) {
            for (int y = 0; y < frame.height(); y++) {
                ByteBuffer row = source.duplicate();
                row.position(sourceBase + y * frame.rowStride());
                row.limit(row.position() + rowBytes);
                destination.position(y * rowBytes);
                destination.put(row);
            }
        } else if (frame.format() == PixelFormat.BGRA8) {
            for (int y = 0; y < frame.height(); y++) {
                int rowStart = sourceBase + y * frame.rowStride();
                destination.position(y * rowBytes);
                for (int x = 0; x < frame.width(); x++) {
                    int pixel = rowStart + x * 4;
                    destination.put(source.get(pixel + 2));
                    destination.put(source.get(pixel + 1));
                    destination.put(source.get(pixel));
                    destination.put(source.get(pixel + 3));
                }
            }
        } else {
            throw new IllegalArgumentException("Unsupported video pixel format: " + frame.format());
        }
        destination.position(0);
    }

    private void remove(UUID screenId) {
        Entry removed = entries.remove(screenId);
        if (removed != null) {
            TextureManager textures = Minecraft.getInstance().getTextureManager();
            textures.release(removed.location());
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
            DynamicTexture texture,
            int width,
            int height) {
    }
}
