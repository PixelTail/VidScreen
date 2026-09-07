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
    private final Map<UUID, Entry> entries = new LinkedHashMap<UUID, Entry>();
    private boolean closed;

    void update(Collection<ScreenState> screens, ScreenPlaybackCoordinator playback) {
        if (closed) {
            return;
        }
        Set<UUID> active = new HashSet<UUID>();
        for (ScreenState screen : screens) {
            UUID id = screen.definition().id();
            MediaDescriptor media = screen.media();
            if (media == null) {
                continue;
            }
            LatestFrameQueue frames = playback.frameQueue(id);
            if (frames == null) {
                continue;
            }
            active.add(id);
            Entry current = entries.get(id);
            if (current != null && !current.media.equals(media)) {
                remove(id);
                current = null;
            }
            VideoFrame frame = frames.poll();
            if (frame == null) {
                continue;
            }
            try {
                if (current == null || current.width != frame.width() || current.height != frame.height()) {
                    remove(id);
                    current = create(id, media, frame.width(), frame.height());
                    entries.put(id, current);
                }
                copy(frame, current.texture.getImage());
                current.texture.upload();
            } finally {
                frame.close();
            }
        }
        UUID[] existing = entries.keySet().toArray(new UUID[entries.size()]);
        for (UUID id : existing) {
            if (!active.contains(id)) {
                remove(id);
            }
        }
    }

    Identifier texture(UUID id) {
        Entry entry = entries.get(id);
        return entry == null ? null : entry.location;
    }

    void clear() {
        UUID[] ids = entries.keySet().toArray(new UUID[entries.size()]);
        for (UUID id : ids) {
            remove(id);
        }
    }

    private static Entry create(UUID id, MediaDescriptor media, int width, int height) {
        Identifier location = new Identifier("vidscreen", "screen/" + id.toString().replace("-", ""));
        NativeImageBackedTexture texture = new NativeImageBackedTexture(width, height, false);
        MinecraftClient.getInstance().getTextureManager().registerTexture(location, texture);
        return new Entry(location, media, texture, width, height);
    }

    private static void copy(VideoFrame frame, NativeImage image) {
        if (image == null || image.getWidth() != frame.width() || image.getHeight() != frame.height()) {
            throw new IllegalArgumentException("Video frame dimensions do not match texture");
        }
        ByteBuffer source = frame.pixels();
        int base = source.position();
        for (int y = 0; y < frame.height(); y++) {
            int row = base + y * frame.rowStride();
            for (int x = 0; x < frame.width(); x++) {
                int offset = row + x * 4;
                int r = source.get(offset) & 0xff;
                int g = source.get(offset + 1) & 0xff;
                int b = source.get(offset + 2) & 0xff;
                int a = source.get(offset + 3) & 0xff;
                if (frame.format() == PixelFormat.BGRA8) {
                    int swap = r;
                    r = b;
                    b = swap;
                } else if (frame.format() != PixelFormat.RGBA8) {
                    throw new IllegalArgumentException("Unsupported video pixel format: " + frame.format());
                }
                image.setColor(x, y, (a << 24) | (b << 16) | (g << 8) | r);
            }
        }
    }

    private void remove(UUID id) {
        Entry removed = entries.remove(id);
        if (removed != null) {
            TextureManager manager = MinecraftClient.getInstance().getTextureManager();
            manager.destroyTexture(removed.location);
        }
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            clear();
        }
    }

    private static final class Entry {
        private final Identifier location;
        private final MediaDescriptor media;
        private final NativeImageBackedTexture texture;
        private final int width;
        private final int height;

        private Entry(Identifier location, MediaDescriptor media, NativeImageBackedTexture texture,
                int width, int height) {
            this.location = location;
            this.media = media;
            this.texture = texture;
            this.width = width;
            this.height = height;
        }
    }
}
