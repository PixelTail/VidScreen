package dev.vidscreen.neoforge;

import io.netty.handler.codec.DecoderException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record VidScreenPayload(byte[] data) implements CustomPacketPayload {
    public static final int MAX_PACKET_BYTES = 1024 * 1024;
    public static final Type<VidScreenPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(VidScreenNeoForge.MOD_ID, "main"));
    public static final StreamCodec<RegistryFriendlyByteBuf, VidScreenPayload> CODEC =
            StreamCodec.ofMember(VidScreenPayload::write, VidScreenPayload::read);

    public VidScreenPayload {
        if (data.length > MAX_PACKET_BYTES) {
            throw new IllegalArgumentException("VidScreen packet exceeds maximum size");
        }
        data = data.clone();
    }

    private static VidScreenPayload read(RegistryFriendlyByteBuf buffer) {
        int length = buffer.readVarInt();
        if (length < 0 || length > MAX_PACKET_BYTES || length > buffer.readableBytes()) {
            throw new DecoderException("Invalid VidScreen packet length: " + length);
        }
        byte[] data = new byte[length];
        buffer.readBytes(data);
        return new VidScreenPayload(data);
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(data.length);
        buffer.writeBytes(data);
    }

    @Override
    public byte[] data() {
        return data.clone();
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
