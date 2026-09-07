package dev.vidscreen.legacy.fabric;

import java.util.Arrays;

import dev.vidscreen.domain.VidScreenLimits;
import dev.vidscreen.protocol.ProtocolException;
import dev.vidscreen.protocol.WireCodec;
import dev.vidscreen.protocol.WireMessage;
import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

/**
 * The 1.19.2 transport seam. The shared wire codec remains Minecraft-free;
 * this class only adds the bounded legacy Fabric buffer envelope.
 */
public final class LegacyFabricPacketAdapter {
    public static final Identifier CHANNEL = new Identifier("vidscreen", "control");

    private LegacyFabricPacketAdapter() {
    }

    public static PacketByteBuf encode(WireMessage message) throws ProtocolException {
        byte[] payload = new WireCodec().encode(message);
        PacketByteBuf buffer = new PacketByteBuf(Unpooled.buffer(payload.length + 5));
        buffer.writeVarInt(payload.length);
        buffer.writeBytes(payload);
        return buffer;
    }

    public static WireMessage decode(PacketByteBuf buffer) throws ProtocolException {
        final int length;
        try {
            length = buffer.readVarInt();
        } catch (RuntimeException error) {
            throw new ProtocolException("Invalid Fabric payload length", error);
        }
        if (length < 0 || length > VidScreenLimits.MAX_WIRE_PAYLOAD_BYTES) {
            throw new ProtocolException("Fabric payload exceeds the bounded protocol limit");
        }
        if (buffer.readableBytes() != length) {
            throw new ProtocolException("Fabric payload length does not match readable bytes");
        }
        byte[] payload = new byte[length];
        buffer.readBytes(payload);
        return new WireCodec().decode(payload);
    }

    /** Useful for tests and diagnostics without exposing a mutable Netty buffer. */
    public static byte[] copyPayload(PacketByteBuf buffer) throws ProtocolException {
        int length = buffer.readableBytes();
        if (length > VidScreenLimits.MAX_WIRE_PAYLOAD_BYTES) {
            throw new ProtocolException("Fabric payload exceeds the bounded protocol limit");
        }
        byte[] payload = new byte[length];
        buffer.getBytes(buffer.readerIndex(), payload);
        return Arrays.copyOf(payload, payload.length);
    }
}
