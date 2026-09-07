package dev.vidscreen.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import dev.vidscreen.domain.BlockPoint;
import dev.vidscreen.domain.DimensionKey;
import dev.vidscreen.domain.Facing;
import dev.vidscreen.domain.MediaDescriptor;
import dev.vidscreen.domain.PlaybackState;
import dev.vidscreen.domain.ScreenDefinition;
import dev.vidscreen.domain.ScreenFit;
import dev.vidscreen.domain.ScreenGeometry;
import dev.vidscreen.domain.ScreenState;

class VisibleScreenSelectorTest {
    @Test
    void selectsOnlyNearestInRangeScreensFromCurrentDimension() {
        ScreenState near = screen("00000000-0000-0000-0000-000000000001", "minecraft:overworld", 2, 32, true);
        ScreenState farther = screen("00000000-0000-0000-0000-000000000002", "minecraft:overworld", 12, 32, true);
        ScreenState tooFar = screen("00000000-0000-0000-0000-000000000003", "minecraft:overworld", 80, 16, true);
        ScreenState otherDimension = screen("00000000-0000-0000-0000-000000000004", "minecraft:the_nether", 1, 32, true);

        List<ScreenState> selected = new VisibleScreenSelector(1).select(
                Arrays.asList(farther, tooFar, otherDimension, near),
                "minecraft:overworld",
                0,
                64,
                0);

        assertEquals(1, selected.size());
        assertEquals(near.definition().id(), selected.get(0).definition().id());
    }

    @Test
    void ignoresScreensWithoutMedia() {
        ScreenState noMedia = screen("00000000-0000-0000-0000-000000000005", "minecraft:overworld", 1, 32, false);

        assertEquals(0, new VisibleScreenSelector(4).select(
                Arrays.asList(noMedia), "minecraft:overworld", 0, 64, 0).size());
    }

    @Test
    void computesFloorDividedAnchorChunks() {
        ScreenState negative = screen(
                "00000000-0000-0000-0000-000000000006", "minecraft:overworld", -17, 32, true);

        assertEquals(-1, VisibleScreenSelector.anchorChunkX(negative));
        assertEquals(0, VisibleScreenSelector.anchorChunkZ(negative));
    }

    private static ScreenState screen(
            String id,
            String dimension,
            int x,
            int viewDistance,
            boolean withMedia) {
        UUID screenId = UUID.fromString(id);
        ScreenDefinition definition = new ScreenDefinition(
                screenId,
                "screen-" + id.substring(id.length() - 1),
                new DimensionKey(dimension),
                ScreenGeometry.between(
                        new BlockPoint(x, 64, 0),
                        new BlockPoint(x + 1, 65, 0),
                        Facing.NORTH),
                ScreenFit.CONTAIN,
                viewDistance);
        return new ScreenState(
                1,
                definition,
                withMedia ? new MediaDescriptor("direct", "https://media.example/video.mp4") : null,
                PlaybackState.stopped(1, 0));
    }
}
