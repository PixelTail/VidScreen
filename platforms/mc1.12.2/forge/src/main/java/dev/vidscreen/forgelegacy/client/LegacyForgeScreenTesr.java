package dev.vidscreen.forgelegacy.client;

import dev.vidscreen.forgelegacy.LegacyScreenTileEntity;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;

/**
 * 1.12.2 TESR seam. It renders no media until the client screenshot gate and
 * a validated screen geometry integration exist.
 */
public final class LegacyForgeScreenTesr extends TileEntitySpecialRenderer<LegacyScreenTileEntity> {
    private final LegacyForgeTextureManager textures;

    public LegacyForgeScreenTesr(LegacyForgeTextureManager textures) {
        this.textures = textures;
    }

    @Override
    public void render(LegacyScreenTileEntity tile, double x, double y, double z,
            float partialTicks, int destroyStage, float alpha) {
        textures.ensureTexture();
    }
}
