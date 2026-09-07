package dev.vidscreen.legacy.forge;

import java.util.function.Supplier;

import dev.vidscreen.domain.VidScreenLimits;
import dev.vidscreen.protocol.ProtocolException;
import dev.vidscreen.protocol.WireCodec;
import dev.vidscreen.protocol.WireMessage;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Forge 1.18.2's SimpleChannel envelope for the shared bounded wire codec.
 * This class is server-safe; it does not reference client rendering or media.
 */
public final class LegacyForgeNetwork {
    public static final ResourceLocation CHANNEL_ID = new ResourceLocation("vidscreen", "control");
    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            CHANNEL_ID,
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals);

    private LegacyForgeNetwork() {
    }

    public static void register() {
        CHANNEL.registerMessage(
                0,
                ControlPacket.class,
                ControlPacket::encode,
                ControlPacket::decode,
                ControlPacket::handle);
    }

    public static final class ControlPacket {
        private final byte[] payload;

        public ControlPacket(WireMessage message) throws ProtocolException {
            this.payload = new WireCodec().encode(message);
        }

        private ControlPacket(byte[] payload) {
            this.payload = payload;
        }

        private static void encode(ControlPacket packet, FriendlyByteBuf buffer) {
            buffer.writeVarInt(packet.payload.length);
            buffer.writeBytes(packet.payload);
        }

        private static ControlPacket decode(FriendlyByteBuf buffer) {
            final int length;
            try {
                length = buffer.readVarInt();
            } catch (RuntimeException error) {
                throw new IllegalArgumentException("Invalid Forge payload length", error);
            }
            if (length < 0 || length > VidScreenLimits.MAX_WIRE_PAYLOAD_BYTES) {
                throw new IllegalArgumentException("Forge payload exceeds the bounded protocol limit");
            }
            if (buffer.readableBytes() != length) {
                throw new IllegalArgumentException("Forge payload length does not match readable bytes");
            }
            byte[] payload = new byte[length];
            buffer.readBytes(payload);
            return new ControlPacket(payload);
        }

        private static void handle(ControlPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
            NetworkEvent.Context context = contextSupplier.get();
            context.enqueueWork(() -> {
                try {
                    new WireCodec().decode(packet.payload);
                } catch (ProtocolException ignored) {
                    // Invalid payloads are dropped at the protocol boundary.
                }
            });
            context.setPacketHandled(true);
        }
    }
}
