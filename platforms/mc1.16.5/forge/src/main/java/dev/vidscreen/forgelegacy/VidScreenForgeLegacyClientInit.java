package dev.vidscreen.forgelegacy;

import dev.vidscreen.forgelegacy.client.LegacyForgeScreenRenderer;
import net.minecraftforge.common.MinecraftForge;

/** Loaded only through DistExecutor on a physical client. */
final class VidScreenForgeLegacyClientInit {
    private VidScreenForgeLegacyClientInit() {
    }

    static void initialize() {
        MinecraftForge.EVENT_BUS.register(new LegacyForgeScreenRenderer());
    }
}
