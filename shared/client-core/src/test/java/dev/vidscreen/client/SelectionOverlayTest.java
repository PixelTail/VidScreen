package dev.vidscreen.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import dev.vidscreen.domain.BlockPoint;
import dev.vidscreen.domain.DimensionKey;
import dev.vidscreen.domain.ViewingArea;

class SelectionOverlayTest {
    @Test
    void viewingAreaHasTwelveGreenEdgesAroundInclusiveBlocks() {
        ViewingArea area = new ViewingArea(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                "area",
                new DimensionKey("minecraft:overworld"),
                new BlockPoint(2, 4, 6),
                new BlockPoint(3, 5, 8));

        List<SelectionOverlay.Line> lines = SelectionOverlay.viewingArea(area);

        assertEquals(12, lines.size());
        float minimumX = Float.POSITIVE_INFINITY;
        float maximumZ = Float.NEGATIVE_INFINITY;
        for (SelectionOverlay.Line line : lines) {
            minimumX = Math.min(minimumX, Math.min(line.start().x(), line.end().x()));
            maximumZ = Math.max(maximumZ, Math.max(line.start().z(), line.end().z()));
            assertTrue(line.green() > line.red());
            assertTrue(line.green() > line.blue());
        }
        assertEquals(1.998f, minimumX, 0.0001f);
        assertEquals(9.002f, maximumZ, 0.0001f);
    }

    @Test
    void selectionPointsGetOneBlockMarkerEach() {
        List<SelectionOverlay.Line> lines = SelectionOverlay.selectionPoints(Arrays.asList(
                new BlockPoint(1, 2, 3),
                new BlockPoint(8, 9, 10)));

        assertEquals(24, lines.size());
        assertEquals(1, lines.get(0).red(), 0.0001f);
        assertEquals(1, lines.get(12).blue(), 0.0001f);
        assertEquals(4, lines.get(0).width(), 0.0001f);
    }
}
