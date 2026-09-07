package dev.vidscreen.forge;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class VidScreenNetwork {
    private static final String PROTOCOL = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation("vidscreen", "main"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals);
    private static volatile Consumer<byte[]> clientHandler;
    private static volatile BiConsumer<ServerPlayer, byte[]> serverHandler;
    private static boolean registered;

    private VidScreenNetwork() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        int discriminator = 0;
        CHANNEL.messageBuilder(VidScreenMessage.class, discriminator++, net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER)
                .encoder(VidScreenMessage::encode)
                .decoder(VidScreenMessage::decode)
                .consumerMainThread(VidScreenMessage::handle)
                .add();
        CHANNEL.messageBuilder(VidScreenMessage.class, discriminator, net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT)
                .encoder(VidScreenMessage::encode)
                .decoder(VidScreenMessage::decode)
                .consumerMainThread(VidScreenMessage::handle)
                .add();
        registered = true;
    }

    public static void sendTo(ServerPlayer player, byte[] data) {
        if (data.length > VidScreenMessage.MAX_BYTES) {
            throw new IllegalArgumentException("VidScreen packet exceeds maximum size");
        }
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new VidScreenMessage(data));
    }

    public static void sendToServer(byte[] data) {
        if (data.length > VidScreenMessage.MAX_BYTES) {
            throw new IllegalArgumentException("VidScreen packet exceeds maximum size");
        }
        CHANNEL.sendToServer(new VidScreenMessage(data));
    }

    static SimpleChannel channel() {
        return CHANNEL;
    }

    public static void setClientHandler(Consumer<byte[]> handler) {
        clientHandler = handler;
    }

    static void setServerHandler(BiConsumer<ServerPlayer, byte[]> handler) {
        serverHandler = handler;
    }

    static Consumer<byte[]> clientHandler() {
        return clientHandler;
    }

    static BiConsumer<ServerPlayer, byte[]> serverHandler() {
        return serverHandler;
    }
}
