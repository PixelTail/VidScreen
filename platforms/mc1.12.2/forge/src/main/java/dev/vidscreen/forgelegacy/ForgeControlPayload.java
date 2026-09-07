package dev.vidscreen.forgelegacy;

import java.util.Arrays;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

/**
 * Bounded 1.12.2 control payload. This channel carries metadata/control only;
 * video frames are never sent through the Minecraft connection.
 */
public final class ForgeControlPayload implements IMessage {
    public static final int MAX_BYTES = 32 * 1024;

    private byte[] payload;

    public ForgeControlPayload() {
        payload = new byte[0];
    }

    public ForgeControlPayload(byte[] payload) {
        validate(payload);
        this.payload = Arrays.copyOf(payload, payload.length);
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        if (buffer.readableBytes() < 4) {
            throw new IllegalArgumentException("Missing VidScreen payload length");
        }
        int length = buffer.readInt();
        if (length < 0 || length > MAX_BYTES || length != buffer.readableBytes()) {
            throw new IllegalArgumentException("Invalid VidScreen payload length: " + length);
        }
        payload = new byte[length];
        buffer.readBytes(payload);
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        validate(payload);
        buffer.writeInt(payload.length);
        buffer.writeBytes(payload);
    }

    public byte[] payload() {
        return Arrays.copyOf(payload, payload.length);
    }

    private static void validate(byte[] value) {
        if (value == null || value.length > MAX_BYTES) {
            throw new IllegalArgumentException("VidScreen payload exceeds the 1.12.2 bound");
        }
    }
}
