package dev.vidscreen.neoforge.client;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import dev.vidscreen.gui.EditorClient;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.minecraft.network.chat.Component;
import dev.vidscreen.client.ClientConnection;
import dev.vidscreen.client.ClientScreenStore;
import dev.vidscreen.neoforge.VidScreenPayload;
import dev.vidscreen.protocol.ProtocolException;
import dev.vidscreen.protocol.WireCodec;
import dev.vidscreen.protocol.WireMessage;

import dev.vidscreen.neoforge.VidScreenNeoForge;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.lifecycle.ClientStoppingEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

@Mod(value = VidScreenNeoForge.MOD_ID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = VidScreenNeoForge.MOD_ID, value = Dist.CLIENT)
public final class VidScreenNeoForgeClient {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final WireCodec CODEC = new WireCodec();
    private static final ClientScreenStore SCREENS = new ClientScreenStore();
    private static ClientPlaybackController playback;
    private static ClientConnection connection;
    private static long connectionWarningAt;
    private static boolean warned;
    private static EditorClient editor;
    private static final KeyMapping MENU_KEY = new KeyMapping("key.vidscreen.menu", 78, KeyMapping.Category.MISC);
    private static final KeyMapping MOVE_KEY = new KeyMapping("key.vidscreen.move", 85, KeyMapping.Category.MISC);

    public VidScreenNeoForgeClient(IEventBus modBus) {
        playback = new ClientPlaybackController(SCREENS, LOGGER);
        connection = new ClientConnection(SCREENS, "neoforge", "26.1.2", playback.capabilities());
        editor = new EditorClient(SCREENS, connection, VidScreenNeoForgeClient::sendEditor, playback::availability);
        modBus.addListener((RegisterKeyMappingsEvent event) -> {
            event.register(MENU_KEY); event.register(MOVE_KEY);
        });
        modBus.addListener(this::registerClientPayloads);
        ScreenRenderer.initialize(SCREENS, playback.textures());
    }

    private void registerClientPayloads(RegisterClientPayloadHandlersEvent event) {
        event.register(VidScreenPayload.TYPE, (payload, context) -> receive(payload));
    }

    @SubscribeEvent
    static void login(ClientPlayerNetworkEvent.LoggingIn event) {
        connect();
    }

    @SubscribeEvent
    static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        disconnect();
    }

    @SubscribeEvent
    static void tick(ClientTickEvent.Post event) {
        long now = System.currentTimeMillis();
        update(now);
        var listener = Minecraft.getInstance().getConnection();
        if (listener == null || !listener.hasChannel(VidScreenPayload.TYPE)) {
            return;
        }
        WireMessage message = connection.poll(now);
        if (message != null) {
            try {
                ClientPacketDistributor.sendToServer(new VidScreenPayload(CODEC.encode(message)));
            } catch (ProtocolException | IllegalStateException error) {
                LOGGER.debug("Could not send VidScreen handshake/clock request ({})", error.getClass().getSimpleName());
            }
        }
    }

    @SubscribeEvent
    static void stopping(ClientStoppingEvent event) {
        connection.disconnect();
        playback.close();
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
        while (MENU_KEY.consumeClick()) { if (EditorClient.screen() == null) { editor.open(); } }
        while (MOVE_KEY.consumeClick()) { if (EditorClient.screen() == null) { editor.move(); } }
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

    @SubscribeEvent
    static void interaction(InputEvent.InteractionKeyMappingTriggered event) {
        if (EditorClient.screen() != null) { return; }
        if (event.isUseItem()) {
            if (editor.consumeUse(event.getHand())) { event.setCanceled(true); event.setSwingHand(false); }
            return;
        }
        if (!editor.selecting()) { return; }
        event.setCanceled(true); event.setSwingHand(false);
        if (event.isAttack()) { editor.selectTarget(); }
    }

    private static void sendEditor(WireMessage message) {
        try { ClientPacketDistributor.sendToServer(new VidScreenPayload(CODEC.encode(message))); }
        catch (ProtocolException error) { throw new IllegalArgumentException("Invalid editor request", error); }
    }
}
