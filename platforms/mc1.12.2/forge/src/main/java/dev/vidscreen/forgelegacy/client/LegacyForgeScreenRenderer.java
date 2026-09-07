package dev.vidscreen.forgelegacy.client;

import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** Client-only world render seam for the experimental 1.12.2 lane. */
public final class LegacyForgeScreenRenderer implements AutoCloseable {
    private static LegacyForgeScreenRenderer active;
    private final LegacyForgeTextureManager textures;

    public LegacyForgeScreenRenderer() {
        textures = new LegacyForgeTextureManager(Minecraft.getMinecraft());
        active = this;
    }

    public static void acceptControl(byte[] metadata) {
        if (metadata == null || metadata.length > 32 * 1024 || active == null) {
            return;
        }
        // Metadata is intentionally not interpreted as a media frame. A future
        // client resolver may update screen state after URL validation.
    }

    @SubscribeEvent
    public void renderWorld(RenderWorldLastEvent event) {
        // The TESR and texture ownership seams are wired, but a world quad is
        // deferred until a mapping-specific client screenshot test exists.
        textures.ensureTexture();
    }

    LegacyForgeTextureManager textures() {
        return textures;
    }

    @Override
    public void close() {
        textures.close();
        if (active == this) {
            active = null;
        }
    }
}
