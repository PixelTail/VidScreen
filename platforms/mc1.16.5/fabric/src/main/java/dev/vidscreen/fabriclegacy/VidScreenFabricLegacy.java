package dev.vidscreen.fabriclegacy;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

/** Dedicated-server-safe 1.16.5 Fabric entrypoint. */
public final class VidScreenFabricLegacy implements ModInitializer {
    public static final String MOD_ID = "vidscreen";
    public static final Identifier CONTROL_CHANNEL = new Identifier(MOD_ID, "control");

    @Override
    public void onInitialize() {
        ServerPlayNetworking.registerGlobalReceiver(CONTROL_CHANNEL, (server, player, handler, buffer, responseSender) -> {
            final byte[] payload = FabricControlPayload.read(buffer);
            server.execute(() -> handleControlPayload(player, payload));
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> sendServerHello(handler.player));
    }

    private static void sendServerHello(net.minecraft.server.network.ServerPlayerEntity player) {
        PacketByteBuf buffer = PacketByteBufs.create();
        FabricControlPayload.write(buffer, new byte[] {'V', 'I', 'D', 'S', 1});
        ServerPlayNetworking.send(player, CONTROL_CHANNEL, buffer);
    }

    private static void handleControlPayload(net.minecraft.server.network.ServerPlayerEntity player, byte[] payload) {
        // Decode with shared WireCodec after authenticating the operation and
        // checking permissions. No payload is treated as a URL or media data.
        if (payload.length == 0) {
            return;
        }
    }
}
