package dev.vidscreen.fabriclegacy;

import net.minecraft.network.PacketByteBuf;

/**
 * Bounded payload framing for the 1.16.5 Fabric channel.
 *
 * The channel carries control metadata only. It must never be used for media
 * bytes, frames, cookies, authorization headers, or arbitrary server data.
 */
public final class FabricControlPayload {
    public static final int MAX_BYTES = 256 * 1024;

    private FabricControlPayload() {
    }

    public static void write(PacketByteBuf buffer, byte[] payload) {
        if (payload == null || payload.length > MAX_BYTES) {
            throw new IllegalArgumentException("VidScreen payload exceeds the 1.16.5 bound");
        }
        buffer.writeVarInt(payload.length);
        buffer.writeBytes(payload);
    }

    public static byte[] read(PacketByteBuf buffer) {
        int length = buffer.readVarInt();
        if (length < 0 || length > MAX_BYTES || length != buffer.readableBytes()) {
            throw new IllegalArgumentException("Invalid VidScreen payload length: " + length);
        }
        byte[] payload = new byte[length];
        buffer.readBytes(payload);
        return payload;
    }
}
