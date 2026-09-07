package dev.vidscreen.forge;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.network.NetworkEvent;

/** The Forge 1.20.1 bounded SimpleChannel payload. */
public final class VidScreenMessage {
    public static final int MAX_BYTES = 1024 * 1024;
    private final byte[] data;

    public VidScreenMessage(byte[] data) {
        if (data == null || data.length > MAX_BYTES) {
            throw new IllegalArgumentException("VidScreen packet exceeds maximum size");
        }
        this.data = data.clone();
    }

    public static void encode(VidScreenMessage message, FriendlyByteBuf buffer) {
        buffer.writeVarInt(message.data.length);
        buffer.writeBytes(message.data);
    }

    public static VidScreenMessage decode(FriendlyByteBuf buffer) {
        int length = buffer.readVarInt();
        if (length < 0 || length > MAX_BYTES || length > buffer.readableBytes()) {
            throw new IllegalArgumentException("Invalid VidScreen packet length: " + length);
        }
        byte[] data = new byte[length];
        buffer.readBytes(data);
        return new VidScreenMessage(data);
    }

    byte[] data() {
        return data.clone();
    }

    static void handle(VidScreenMessage message, java.util.function.Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            if (context.getDirection().getReceptionSide() == LogicalSide.CLIENT) {
                Consumer<byte[]> handler = VidScreenNetwork.clientHandler();
                if (handler != null) {
                    handler.accept(message.data());
                }
            } else {
                ServerPlayer sender = context.getSender();
                BiConsumer<ServerPlayer, byte[]> handler = VidScreenNetwork.serverHandler();
                if (sender != null && handler != null) {
                    handler.accept(sender, message.data());
                }
            }
        });
        context.setPacketHandled(true);
    }
}
