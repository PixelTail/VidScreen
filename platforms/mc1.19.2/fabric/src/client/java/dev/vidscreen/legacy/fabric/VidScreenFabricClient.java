package dev.vidscreen.legacy.fabric;

import dev.vidscreen.protocol.ProtocolException;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/**
 * Client entrypoint kept separate from the server entrypoint. Rendering and
 * media-native work are intentionally not part of this feasibility lane.
 */
public final class VidScreenFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(
                LegacyFabricPacketAdapter.CHANNEL,
                (client, handler, buffer, responseSender) -> {
                    try {
                        LegacyFabricPacketAdapter.decode(buffer);
                    } catch (ProtocolException ignored) {
                        // Drop malformed server payloads at the connection boundary.
                    }
                });
    }
}
