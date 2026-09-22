package dev.vidscreen.neoforge;

import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import dev.vidscreen.protocol.PayloadFraming;
import dev.vidscreen.protocol.ProtocolException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record VidScreenPayload(byte[] data) implements CustomPacketPayload {
    public static final int MAX_PACKET_BYTES = PayloadFraming.MAX_PAYLOAD_BYTES;
    public static final Type<VidScreenPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(VidScreenNeoForge.MOD_ID, "main"));
    public static final StreamCodec<RegistryFriendlyByteBuf, VidScreenPayload> CODEC =
            StreamCodec.ofMember(VidScreenPayload::write, VidScreenPayload::read);

    public VidScreenPayload {
        if (data == null || data.length == 0 || data.length > MAX_PACKET_BYTES) {
            throw new IllegalArgumentException("VidScreen packet exceeds maximum size");
        }
        data = data.clone();
    }

    private static VidScreenPayload read(RegistryFriendlyByteBuf buffer) {
        int length = buffer.readableBytes();
        if (length == 0 || length > PayloadFraming.MAX_FRAME_BYTES) {
            throw new DecoderException("Invalid VidScreen packet length: " + length);
        }
        byte[] data = new byte[length];
        buffer.readBytes(data);
        try {
            return new VidScreenPayload(PayloadFraming.decode(data));
        } catch (ProtocolException error) {
            throw new DecoderException(error);
        }
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        try {
            buffer.writeBytes(PayloadFraming.encode(data));
        } catch (ProtocolException error) {
            throw new EncoderException(error);
        }
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
