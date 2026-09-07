package dev.vidscreen.fabric;

import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

/** Bounded 1.20.1 PacketByteBuf adapter for the shared WireCodec bytes. */
public final class VidScreenPayload {
    public static final int MAX_PACKET_BYTES = 1024 * 1024;
    public static final Identifier CHANNEL = new Identifier("vidscreen", "main");

    private final byte[] data;

    public VidScreenPayload(byte[] data) {
        if (data == null || data.length > MAX_PACKET_BYTES) {
            throw new IllegalArgumentException("VidScreen packet exceeds maximum size");
        }
        this.data = data.clone();
    }

    public byte[] data() {
        return data.clone();
    }

    public PacketByteBuf toBuffer() {
        PacketByteBuf buffer = new PacketByteBuf(Unpooled.buffer(data.length + 5, MAX_PACKET_BYTES + 5));
        buffer.writeVarInt(data.length);
        buffer.writeBytes(data);
        return buffer;
    }

    public static VidScreenPayload read(PacketByteBuf buffer) {
        int length = buffer.readVarInt();
        if (length < 0 || length > MAX_PACKET_BYTES || length > buffer.readableBytes()) {
            throw new IllegalArgumentException("Invalid VidScreen packet length: " + length);
        }
        return new VidScreenPayload(buffer.readByteArray(length));
    }
}
