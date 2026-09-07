package dev.vidscreen.forgelegacy.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.util.ResourceLocation;

/** Client-only dynamic texture owner. Native media is intentionally absent. */
final class LegacyForgeTextureManager implements AutoCloseable {
    private final TextureManager textureManager;
    private final ResourceLocation id = new ResourceLocation("vidscreen", "legacy_screen");
    private DynamicTexture texture;

    LegacyForgeTextureManager(Minecraft client) {
        textureManager = client.getTextureManager();
    }

    void ensureTexture() {
        if (texture != null) {
            return;
        }
        texture = new DynamicTexture(1, 1, false);
        texture.getTextureData()[0] = 0xFFFFFFFF;
        texture.updateDynamicTexture();
        textureManager.loadTexture(id, texture);
    }

    ResourceLocation id() {
        return id;
    }

    void uploadRgba(byte[] rgba) {
        if (rgba == null || rgba.length != 4) {
            throw new IllegalArgumentException("Legacy texture frame must be one bounded RGBA pixel");
        }
        ensureTexture();
        texture.getTextureData()[0] = (rgba[3] & 0xFF) << 24
                | (rgba[0] & 0xFF) << 16
                | (rgba[1] & 0xFF) << 8
                | (rgba[2] & 0xFF);
        texture.updateDynamicTexture();
        java.util.Arrays.fill(rgba, (byte) 0);
    }

    @Override
    public void close() {
        if (texture != null) {
            texture.deleteGlTexture();
            texture = null;
        }
    }
}
