package dev.vidscreen.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public final class VidScreenFabric implements ModInitializer {
    public static final String MOD_ID = "vidscreen";

    @Override
    public void onInitialize() {
        // Registration is channel-based in the 1.20.1 API; payload bytes remain
        // owned by the shared bounded WireCodec.
        FabricServerRuntime.initialize();
    }
}
