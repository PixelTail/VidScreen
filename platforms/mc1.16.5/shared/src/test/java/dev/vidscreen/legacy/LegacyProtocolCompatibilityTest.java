package dev.vidscreen.legacy;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import dev.vidscreen.protocol.ProtocolException;
import dev.vidscreen.protocol.WireCodec;
import dev.vidscreen.protocol.message.ClientHello;

final class LegacyProtocolCompatibilityTest {
    @Test
    void reusesBoundedProtocolForJava8Lane() throws Exception {
        ClientHello hello = new ClientHello(1, 0, "0.1.0", "fabric", "1.16.5", 0L, 2048);
        WireCodec codec = new WireCodec();
        assertArrayEquals(codec.encode(hello), codec.encode(codec.decode(codec.encode(hello))));
    }

    @Test
    void rejectsOversizedWirePayloadBeforeAllocation() {
        byte[] oversized = new byte[1024 * 1024 + 1];
        assertThrows(ProtocolException.class, () -> new WireCodec().decode(oversized));
    }
}
