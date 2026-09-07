package dev.vidscreen.fabric;

import io.netty.handler.codec.DecoderException;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record VidScreenPayload(byte[] data) implements CustomPayload {
    public static final int MAX_PACKET_BYTES = 1024 * 1024;
    public static final CustomPayload.Id<VidScreenPayload> ID = new CustomPayload.Id<>(
            Identifier.of(VidScreenFabric.MOD_ID, "main"));
    public static final PacketCodec<PacketByteBuf, VidScreenPayload> CODEC =
            PacketCodec.of(VidScreenPayload::write, VidScreenPayload::read);

    public VidScreenPayload {
        if (data == null || data.length > MAX_PACKET_BYTES) {
            throw new IllegalArgumentException("VidScreen packet exceeds maximum size");
        }
        data = data.clone();
    }

    private static VidScreenPayload read(PacketByteBuf buffer) {
        int length = buffer.readVarInt();
        if (length < 0 || length > MAX_PACKET_BYTES || length > buffer.readableBytes()) {
            throw new DecoderException("Invalid VidScreen packet length: " + length);
        }
        byte[] data = new byte[length];
        buffer.readBytes(data);
        return new VidScreenPayload(data);
    }

    private static void write(VidScreenPayload payload, PacketByteBuf buffer) {
        buffer.writeVarInt(payload.data.length);
        buffer.writeBytes(payload.data);
    }

    @Override
    public byte[] data() {
        return data.clone();
    }

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
