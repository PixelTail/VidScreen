package dev.vidscreen.fabric.client;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.event.client.player.ClientPreAttackCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import dev.vidscreen.gui.EditorClient;
import net.minecraft.network.chat.Component;
import dev.vidscreen.client.ClientConnection;
import dev.vidscreen.client.ClientScreenStore;
import dev.vidscreen.fabric.VidScreenPayload;
import dev.vidscreen.protocol.ProtocolException;
import dev.vidscreen.protocol.WireCodec;
import dev.vidscreen.protocol.WireMessage;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class VidScreenFabricClient implements ClientModInitializer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final WireCodec CODEC = new WireCodec();
    private static final ClientScreenStore SCREENS = new ClientScreenStore();
    private static ClientPlaybackController playback;
    private static ClientConnection connection;
    private static long connectionWarningAt;
    private static boolean warned;
    private static EditorClient editor;
    private static KeyMapping menuKey;
    private static KeyMapping moveKey;

    @Override
    public void onInitializeClient() {
        playback = new ClientPlaybackController(SCREENS, LOGGER);
        connection = new ClientConnection(SCREENS, "fabric", "26.1.2", playback.capabilities());
        editor = new EditorClient(SCREENS, connection, VidScreenFabricClient::sendEditor, playback::availability);
        menuKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.vidscreen.menu", 78, KeyMapping.Category.MISC));
        moveKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.vidscreen.move", 85, KeyMapping.Category.MISC));
        ClientPreAttackCallback.EVENT.register((client, player, clicks) -> {
            if (!editor.selecting()) { return false; }
            if (clicks > 0) { editor.selectTarget(); }
            return true;
        });
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (player != Minecraft.getInstance().player || !editor.consumeUse(hand)) { return InteractionResult.PASS; }

            return InteractionResult.FAIL;
        });
        UseItemCallback.EVENT.register((player, level, hand) -> {
            if (player != Minecraft.getInstance().player || !editor.consumeUse(hand)) { return InteractionResult.PASS; }

            return InteractionResult.FAIL;
        });
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (player != Minecraft.getInstance().player || !editor.consumeUse(hand)) { return InteractionResult.PASS; }

            return InteractionResult.FAIL;
        });
        ClientPlayNetworking.registerGlobalReceiver(VidScreenPayload.TYPE, (payload, context) -> receive(payload));
        ClientPlayConnectionEvents.JOIN.register((listener, sender, client) -> connect());
        ClientPlayConnectionEvents.DISCONNECT.register((listener, client) -> disconnect());
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
        ScreenRenderer.initialize(SCREENS, playback.textures());
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            connection.disconnect();
            ScreenRenderer.close();
            playback.close();
        });
    }

    private static void tick() {
        long now = System.currentTimeMillis();
        update(now);
        if (!ClientPlayNetworking.canSend(VidScreenPayload.TYPE)) {
            return;
        }
        WireMessage message = connection.poll(now);
        if (message != null) {
            try {
                ClientPlayNetworking.send(new VidScreenPayload(CODEC.encode(message)));
            } catch (ProtocolException | IllegalStateException error) {
                LOGGER.debug("Could not send VidScreen handshake/clock request ({})", error.getClass().getSimpleName());
            }
        }
    }

    private static void connect() {
        editor.reset();
        playback.clear();
        connection.connect();
        warned = false;
        connectionWarningAt = System.currentTimeMillis() + 10_000;
    }

    private static void disconnect() {
        editor.reset();
        connection.disconnect();
        playback.clear();
    }

    private static void receive(VidScreenPayload payload) {
        try {
            ClientConnection.State before = connection.state();
            WireMessage message = CODEC.decode(payload.data());
            connection.accept(message, System.currentTimeMillis());
            if (connection.state() == ClientConnection.State.READY) { editor.accept(message); }
            if (before != connection.state()) {
                if (connection.state() == ClientConnection.State.READY) {
                    notice("VidScreen connected. " + playback.availability());
                } else if (connection.state() == ClientConnection.State.REJECTED) {
                    playback.clear();
                    notice("VidScreen connection rejected. Install matching server and client versions.");
                }
            }
        } catch (ProtocolException | RuntimeException error) {
            LOGGER.warn("Rejected VidScreen server payload ({})", error.getClass().getSimpleName());
        }
    }

    private static void update(long now) {
        editor.tick();
        while (menuKey.consumeClick()) { if (EditorClient.screen() == null) { editor.open(); } }
        while (moveKey.consumeClick()) { if (EditorClient.screen() == null) { editor.move(); } }
        playback.tick(connection.estimatedServerTimeMillis(now));
        if (connection.state() == ClientConnection.State.CONNECTING && !warned && now >= connectionWarningAt) {
            warned = true;
            notice("VidScreen has not connected. Check that this server has the matching VidScreen plugin/mod.");
        }
    }

    private static void notice(String text) {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            dev.vidscreen.gui.EditorClient.message(Component.literal(text), false);
        }
    }

    private static void sendEditor(WireMessage message) {
        try { ClientPlayNetworking.send(new VidScreenPayload(CODEC.encode(message))); }
        catch (ProtocolException error) { throw new IllegalArgumentException("Invalid editor request", error); }
    }
}
