package dev.vidscreen.fabriclegacy.client;

import net.minecraft.client.MinecraftClient;

/**
 * Fabric 1.16.5 render seam. The current scaffold renders a bounded white
 * texture through the client event path; world-quad geometry is intentionally
 * isolated here so a later mapping-specific implementation cannot leak into
 * the server or shared protocol.
 */
final class LegacyFabricScreenRenderer {
    private final LegacyFabricTextureManager textures;

    LegacyFabricScreenRenderer(MinecraftClient client) {
        textures = new LegacyFabricTextureManager(client);
        textures.ensureTexture();
    }

    void acceptControlPayload(byte[] payload) {
        if (payload == null || payload.length == 0) {
            return;
        }
        // Screen definitions and playback state are decoded by shared WireCodec
        // in the next adapter step. This method deliberately does not accept
        // or enqueue media bytes from the server.
    }

    void renderVisibleScreens() {
        // The actual 1.16.5 world-quad draw uses the current camera matrix and
        // BufferBuilder API. Keep this hook client-only until the exact Yarn
        // render signatures are covered by a client smoke test.
        textures.ensureTexture();
    }
}
