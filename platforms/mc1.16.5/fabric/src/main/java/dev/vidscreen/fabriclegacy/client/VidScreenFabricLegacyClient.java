package dev.vidscreen.fabriclegacy.client;

import dev.vidscreen.fabriclegacy.FabricControlPayload;
import dev.vidscreen.fabriclegacy.VidScreenFabricLegacy;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.PacketByteBuf;

/** Client-only Fabric hooks. No renderer class is referenced by the server entrypoint. */
public final class VidScreenFabricLegacyClient implements ClientModInitializer {
    private LegacyFabricScreenRenderer renderer;

    @Override
    public void onInitializeClient() {
        renderer = new LegacyFabricScreenRenderer(MinecraftClient.getInstance());
        ClientPlayNetworking.registerGlobalReceiver(VidScreenFabricLegacy.CONTROL_CHANNEL,
                (client, handler, buffer, responseSender) -> {
                    final byte[] payload = FabricControlPayload.read(buffer);
                    client.execute(() -> renderer.acceptControlPayload(payload));
                });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> sendClientHello());
        WorldRenderEvents.AFTER_ENTITIES.register(context -> renderer.renderVisibleScreens());
    }

    private void sendClientHello() {
        PacketByteBuf buffer = PacketByteBufs.create();
        FabricControlPayload.write(buffer, new byte[] {'V', 'I', 'D', 'C', 1});
        ClientPlayNetworking.send(VidScreenFabricLegacy.CONTROL_CHANNEL, buffer);
    }
}
