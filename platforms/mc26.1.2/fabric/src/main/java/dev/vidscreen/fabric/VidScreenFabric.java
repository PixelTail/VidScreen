package dev.vidscreen.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

public final class VidScreenFabric implements ModInitializer {
    public static final String MOD_ID = "vidscreen";

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.clientboundPlay().register(VidScreenPayload.TYPE, VidScreenPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(VidScreenPayload.TYPE, VidScreenPayload.CODEC);
        FabricServerRuntime.initialize();
    }
}
