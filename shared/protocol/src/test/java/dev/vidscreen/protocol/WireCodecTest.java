package dev.vidscreen.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import dev.vidscreen.domain.BlockPoint;
import dev.vidscreen.domain.DimensionKey;
import dev.vidscreen.domain.Facing;
import dev.vidscreen.domain.MediaDescriptor;
import dev.vidscreen.domain.PlaybackState;
import dev.vidscreen.domain.PlaybackStatus;
import dev.vidscreen.domain.ScreenDefinition;
import dev.vidscreen.domain.ScreenFit;
import dev.vidscreen.domain.ScreenGeometry;
import dev.vidscreen.domain.ScreenState;
import dev.vidscreen.domain.VidScreenLimits;
import dev.vidscreen.protocol.message.ClientHello;
import dev.vidscreen.protocol.message.ClockRequest;
import dev.vidscreen.protocol.message.ClockResponse;
import dev.vidscreen.protocol.message.OperationResult;
import dev.vidscreen.protocol.message.PlaybackUpdate;
import dev.vidscreen.protocol.message.ScreenDelete;
import dev.vidscreen.protocol.message.ScreenSnapshot;
import dev.vidscreen.protocol.message.ScreenUpsert;
import dev.vidscreen.protocol.message.ServerHello;

class WireCodecTest {
    private final WireCodec codec = new WireCodec();

    @Test
    void roundTripsEveryInitialMessageType() throws Exception {
        ScreenState screen = sampleScreen();
        UUID id = screen.definition().id();

        WireMessage[] messages = {
                new ClientHello(1, 0, "0.1.0", "fabric", "26.2", Capabilities.MP4 | Capabilities.HLS, 8_192),
                new ServerHello(1, 0, true, Capabilities.MP4, 123_456, "ok"),
                new ClockRequest(7, 100),
                new ClockResponse(7, 100, 110, 111),
                new ScreenSnapshot(4, Collections.singletonList(screen)),
                new ScreenUpsert(screen),
                new ScreenDelete(id, 8),
                new PlaybackUpdate(id, screen.playback()),
                new OperationResult(UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"), true, "ok", "created")
        };

        for (WireMessage message : messages) {
            assertEquals(message, codec.decode(codec.encode(message)), message.type().name());
        }
    }

    @Test
    void clientHelloMatchesGoldenFixture() throws Exception {
        ClientHello hello = new ClientHello(
                1, 0, "0.1.0", "fabric", "26.2", Capabilities.MP4 | Capabilities.HLS, 8_192);

        assertEquals(loadFixture("client-hello-v1.hex"), toHex(codec.encode(hello)));
    }

    @Test
    void rejectsInvalidMagicTruncationAndTrailingBytes() throws Exception {
        byte[] valid = codec.encode(new ClockRequest(1, 2));
        byte[] invalidMagic = valid.clone();
        invalidMagic[0] = 0;
        byte[] truncated = Arrays.copyOf(valid, valid.length - 1);
        byte[] trailing = Arrays.copyOf(valid, valid.length + 1);

        assertThrows(ProtocolException.class, () -> codec.decode(invalidMagic));
        assertThrows(ProtocolException.class, () -> codec.decode(truncated));
        assertThrows(ProtocolException.class, () -> codec.decode(trailing));
    }

    @Test
    void acceptsAppendedFieldsFromNewerMinorVersion() throws Exception {
        ClockRequest request = new ClockRequest(1, 2);
        byte[] current = codec.encode(request);
        byte[] future = Arrays.copyOf(current, current.length + 4);
        future[6] = 0;
        future[7] = (byte) (ProtocolVersion.MINOR + 1);
        future[current.length] = 1;
        future[current.length + 1] = 2;
        future[current.length + 2] = 3;
        future[current.length + 3] = 4;

        assertEquals(request, codec.decode(future));
    }

    @Test
    void rejectsOversizedPayloadBeforeParsing() {
        byte[] payload = new byte[VidScreenLimits.MAX_WIRE_PAYLOAD_BYTES + 1];
        assertThrows(ProtocolException.class, () -> codec.decode(payload));
    }

    private static String loadFixture(String name) throws Exception {
        InputStream input = WireCodecTest.class.getResourceAsStream("/protocol-fixtures/" + name);
        if (input == null) {
            throw new AssertionError("Missing protocol fixture: " + name);
        }
        try (InputStream stream = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[256];
            int read;
            while ((read = stream.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8).trim();
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            result.append(String.format(java.util.Locale.ROOT, "%02x", value & 0xff));
        }
        return result.toString();
    }

    private static ScreenState sampleScreen() {
        UUID id = UUID.fromString("01234567-89ab-cdef-0123-456789abcdef");
        ScreenDefinition definition = new ScreenDefinition(
                id,
                "lobby",
                new DimensionKey("minecraft:overworld"),
                ScreenGeometry.between(new BlockPoint(1, 64, 8), new BlockPoint(4, 66, 8), Facing.NORTH),
                ScreenFit.CONTAIN,
                96);
        PlaybackState playback = new PlaybackState(3, PlaybackStatus.PLAYING, 5_000, 10_000, 1.0, true);
        return new ScreenState(4, definition, new MediaDescriptor("direct", "https://media.example/video.mp4"), playback);
    }
}
