package dev.vidscreen.protocol;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import dev.vidscreen.protocol.message.ClientHello;
import dev.vidscreen.protocol.message.ClockRequest;
import dev.vidscreen.protocol.message.ServerHello;

class PayloadFramingTest {
    @Test
    void pluginAndModHandshakeUseTheSameEnvelope() throws Exception {
        WireCodec codec = new WireCodec();
        ClientHello hello = new ClientHello(1, 0, "0.1.0", "fabric", "26.2", Capabilities.MP4, 4096);
        byte[] clientPacket = PayloadFraming.encode(codec.encode(hello));
        // The former Paper path attempted this, failing before accepting any client.
        assertThrows(ProtocolException.class, () -> codec.decode(clientPacket));
        assertEquals(hello, codec.decode(PayloadFraming.decode(clientPacket)));
        ServerHello accepted = new ServerHello(1, 0, true, Capabilities.MP4, 1000, "ok");
        assertEquals(accepted, codec.decode(PayloadFraming.decode(PayloadFraming.encode(codec.encode(accepted)))));
    }

    @Test
    void envelopeMatchesMinecraftVarIntNotASecondWireHeader() throws Exception {
        byte[] wire = new WireCodec().encode(new ClockRequest(1, 2));
        byte[] framed = PayloadFraming.encode(wire);
        assertEquals(25, wire.length);
        assertEquals(0x19, framed[0]);
        assertArrayEquals(wire, Arrays.copyOfRange(framed, 1, framed.length));
    }

    @Test
    void roundTripsAllPrefixBoundaries() throws Exception {
        for (int size : new int[] {1, 127, 128, 16383, 16384, PayloadFraming.MAX_PAYLOAD_BYTES}) {
            byte[] body = new byte[size];
            Arrays.fill(body, (byte) 0x56);
            assertArrayEquals(body, PayloadFraming.decode(PayloadFraming.encode(body)));
        }
    }

    @Test
    void rejectsMalformedTruncatedTrailingAndUnboundedFrames() throws Exception {
        byte[] good = PayloadFraming.encode(new WireCodec().encode(new ClockRequest(1, 2)));
        for (byte[] invalid : new byte[][] {null, {}, {0}, {(byte) 0x80},
                {(byte) 0x80, (byte) 0x80, (byte) 0x80, 0},
                {(byte) 0x81, 0, 0}, {(byte) 0xff, (byte) 0xff, 0x7f},
                Arrays.copyOf(good, good.length - 1), Arrays.copyOf(good, good.length + 1),
                new byte[PayloadFraming.MAX_FRAME_BYTES + 1]}) {
            assertThrows(ProtocolException.class, () -> PayloadFraming.decode(invalid));
        }
        assertThrows(ProtocolException.class,
                () -> PayloadFraming.encode(new byte[PayloadFraming.MAX_PAYLOAD_BYTES + 1]));
    }
}
