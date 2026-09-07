package dev.vidscreen.forgelegacy.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.util.ResourceLocation;

/** Client-only dynamic texture owner. Native media is intentionally not bundled. */
final class LegacyForgeTextureManager implements AutoCloseable {
    private final TextureManager textureManager;
    private final ResourceLocation id = new ResourceLocation("vidscreen", "legacy_screen");
    private DynamicTexture texture;

    LegacyForgeTextureManager(Minecraft client) {
        textureManager = client.getTextureManager();
    }

    void ensureTexture() {
        if (texture == null) {
            texture = new DynamicTexture(1, 1, false);
            texture.getPixels().setPixelRGBA(0, 0, 0xFFFFFFFF);
            texture.upload();
            textureManager.register(id, texture);
        }
    }

    ResourceLocation id() {
        return id;
    }

    @Override
    public void close() {
        if (texture != null) {
            texture.close();
            textureManager.release(id);
            texture = null;
        }
    }
}
