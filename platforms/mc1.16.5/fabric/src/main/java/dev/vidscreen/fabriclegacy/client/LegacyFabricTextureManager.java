package dev.vidscreen.fabriclegacy.client;

import java.util.Arrays;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.util.Identifier;

/**
 * Bounded client-side texture owner for the legacy lane.
 *
 * A real decoder can hand RGBA frames to this seam later. Uploads must remain
 * on the client thread; the server channel never supplies frame bytes.
 */
final class LegacyFabricTextureManager implements AutoCloseable {
    private static final int WIDTH = 1;
    private static final int HEIGHT = 1;

    private final TextureManager textureManager;
    private final Identifier textureId = new Identifier("vidscreen", "legacy_screen");
    private NativeImageBackedTexture texture;

    LegacyFabricTextureManager(MinecraftClient client) {
        this.textureManager = client.getTextureManager();
    }

    void ensureTexture() {
        if (texture != null) {
            return;
        }
        texture = new NativeImageBackedTexture(WIDTH, HEIGHT, false);
        texture.getImage().setPixelColor(0, 0, 0xFFFFFFFF);
        texture.upload();
        textureManager.registerTexture(textureId, texture);
    }

    Identifier id() {
        return textureId;
    }

    void uploadRgba(byte[] rgba) {
        if (rgba == null || rgba.length != WIDTH * HEIGHT * 4) {
            throw new IllegalArgumentException("Legacy texture frame must be one bounded RGBA pixel");
        }
        ensureTexture();
        int color = (rgba[3] & 0xFF) << 24
                | (rgba[0] & 0xFF) << 16
                | (rgba[1] & 0xFF) << 8
                | (rgba[2] & 0xFF);
        texture.getImage().setPixelColor(0, 0, color);
        texture.upload();
        Arrays.fill(rgba, (byte) 0);
    }

    @Override
    public void close() {
        if (texture != null) {
            texture.close();
            texture = null;
        }
    }
}
