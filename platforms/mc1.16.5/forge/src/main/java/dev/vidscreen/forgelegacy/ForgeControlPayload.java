package dev.vidscreen.forgelegacy;

import net.minecraft.network.PacketBuffer;

/** Bounded 1.16.5 Forge control payload; media bytes are never accepted here. */
public final class ForgeControlPayload {
    public static final int MAX_BYTES = 256 * 1024;
    private byte[] payload;

    public ForgeControlPayload() {
        this(new byte[0]);
    }

    public ForgeControlPayload(byte[] payload) {
        if (payload == null || payload.length > MAX_BYTES) {
            throw new IllegalArgumentException("VidScreen payload exceeds the 1.16.5 bound");
        }
        this.payload = payload.clone();
    }

    public byte[] payload() {
        return payload.clone();
    }

    public void encode(PacketBuffer buffer) {
        if (payload.length > MAX_BYTES) {
            throw new IllegalStateException("VidScreen payload exceeds the 1.16.5 bound");
        }
        buffer.writeVarInt(payload.length);
        buffer.writeBytes(payload);
    }

    public void decode(PacketBuffer buffer) {
        int length = buffer.readVarInt();
        if (length < 0 || length > MAX_BYTES || length != buffer.readableBytes()) {
            throw new IllegalArgumentException("Invalid VidScreen payload length: " + length);
        }
        byte[] next = new byte[length];
        buffer.readBytes(next);
        payload = next;
    }

    public static ForgeControlPayload from(PacketBuffer buffer) {
        ForgeControlPayload message = new ForgeControlPayload();
        message.decode(buffer);
        return message;
    }
}
