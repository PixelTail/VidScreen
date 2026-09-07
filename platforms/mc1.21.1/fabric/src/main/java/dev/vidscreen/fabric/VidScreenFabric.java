package dev.vidscreen.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

public final class VidScreenFabric implements ModInitializer {
    public static final String MOD_ID = "vidscreen";

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.playS2C().register(VidScreenPayload.ID, VidScreenPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(VidScreenPayload.ID, VidScreenPayload.CODEC);
        FabricServerRuntime.initialize();
    }
}
