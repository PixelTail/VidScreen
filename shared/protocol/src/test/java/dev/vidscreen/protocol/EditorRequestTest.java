package dev.vidscreen.protocol;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import dev.vidscreen.domain.*;
import dev.vidscreen.protocol.message.EditorRequest;

class EditorRequestTest {
    @Test
    void roundTripsTypedOperationsThroughThePluginEnvelope() throws Exception {
        UUID id = UUID.randomUUID();
        ScreenDefinition definition = new ScreenDefinition(id, "cinema", new DimensionKey("minecraft:overworld"),
                ScreenGeometry.between(new BlockPoint(1, 64, 2), new BlockPoint(8, 68, 2), Facing.NORTH),
                ScreenFit.CONTAIN, 96);
        WireCodec codec = new WireCodec();
        for (EditorRequest.Action action : EditorRequest.Action.values()) {
            EditorRequest request = action == EditorRequest.Action.CREATE || action == EditorRequest.Action.UPDATE
                    ? EditorRequest.definition(action, definition)
                    : action == EditorRequest.Action.AREA_CREATE || action == EditorRequest.Action.AREA_UPDATE
                    ? EditorRequest.area(action, new ViewingArea(id, "cinema", definition.dimension(),
                            new BlockPoint(0, 0, 0), new BlockPoint(10, 10, 10)))
                    : action == EditorRequest.Action.SOURCE
                    ? EditorRequest.source(id, new MediaDescriptor("direct", "https://example.org/film.mp4"))
                    : EditorRequest.operation(action, id, action == EditorRequest.Action.RATE ? 1 : 0);
            byte[] bytes = PayloadFraming.encode(codec.encode(request));
            assertEquals(request, codec.decode(PayloadFraming.decode(bytes)));
            assertThrows(ProtocolException.class,
                    () -> codec.decode(PayloadFraming.decode(Arrays.copyOf(bytes, bytes.length - 1))));
        }
    }

    @Test
    void rejectsMissingObjectsAndOutOfRangeNumbersBeforeDispatch() {
        UUID id = UUID.randomUUID();
        assertThrows(NullPointerException.class, () -> EditorRequest.operation(EditorRequest.Action.CREATE, id, 0));
        assertThrows(IllegalArgumentException.class, () -> EditorRequest.operation(EditorRequest.Action.SOURCE, id, 0));
        for (double value : new double[] {Double.NaN, Double.POSITIVE_INFINITY, -1, 32_000_000_001.0}) {
            assertThrows(IllegalArgumentException.class, () -> EditorRequest.operation(EditorRequest.Action.SEEK, id, value));
        }
        assertThrows(IllegalArgumentException.class, () -> EditorRequest.operation(EditorRequest.Action.RATE, id, 0));
        assertThrows(IllegalArgumentException.class, () -> EditorRequest.operation(EditorRequest.Action.LOOP, id, 2));
    }
}
