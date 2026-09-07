package dev.vidscreen.forgelegacy.client;

import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Forge 1.16.5 client render seam. Geometry and texture upload are kept out
 * of common/server classes so a dedicated server never initializes them.
 */
public final class LegacyForgeScreenRenderer {
    private final LegacyForgeTextureManager textures =
            new LegacyForgeTextureManager(Minecraft.getInstance());

    @SubscribeEvent
    public void renderWorld(RenderWorldLastEvent event) {
        // A mapping-specific world quad is added only with a client screenshot
        // test. For now this proves the event and texture ownership boundary.
        textures.ensureTexture();
    }
}
