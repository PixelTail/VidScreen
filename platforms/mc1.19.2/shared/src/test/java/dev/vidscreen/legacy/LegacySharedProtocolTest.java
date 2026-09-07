package dev.vidscreen.legacy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import dev.vidscreen.protocol.WireCodec;
import dev.vidscreen.protocol.WireMessage;
import dev.vidscreen.protocol.message.ClockRequest;

class LegacySharedProtocolTest {
    @Test
    void sharedProtocolRoundTripsThroughJava8SourceSet() throws Exception {
        WireMessage request = new ClockRequest(7L, 11L);
        WireMessage decoded = new WireCodec().decode(new WireCodec().encode(request));

        assertEquals(request, decoded);
    }
}
