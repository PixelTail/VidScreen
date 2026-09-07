package dev.vidscreen.legacy.fabric;

import dev.vidscreen.protocol.ProtocolException;
import dev.vidscreen.protocol.WireMessage;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public final class VidScreenFabric implements ModInitializer {
    public static final String MOD_ID = "vidscreen";

    @Override
    public void onInitialize() {
        ServerPlayNetworking.registerGlobalReceiver(
                LegacyFabricPacketAdapter.CHANNEL,
                (server, player, handler, buffer, responseSender) -> {
                    try {
                        WireMessage message = LegacyFabricPacketAdapter.decode(buffer);
                        // The feasibility lane validates and bounds transport input only.
                        // Authoritative screen state remains in the shared server service.
                        if (message == null) {
                            throw new ProtocolException("Decoded Fabric message was null");
                        }
                    } catch (ProtocolException ignored) {
                        // Invalid client payloads are dropped rather than allocated or executed.
                    }
                });
    }
}
