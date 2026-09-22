package dev.vidscreen.client;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import dev.vidscreen.domain.*;

class ScreenSelectionTest {
    private final DimensionKey world = new DimensionKey("minecraft:overworld");

    @Test
    void requiresClockwiseWindingForEveryFacing() {
        for (Facing facing : Facing.values()) {
            BlockPoint[] corners = facing.axis() == Axis.Z
                    ? new BlockPoint[] {new BlockPoint(0,3,0),new BlockPoint(3,3,0),new BlockPoint(3,0,0),new BlockPoint(0,0,0)}
                    : facing.axis() == Axis.X
                    ? new BlockPoint[] {new BlockPoint(0,3,3),new BlockPoint(0,3,0),new BlockPoint(0,0,0),new BlockPoint(0,0,3)}
                    : new BlockPoint[] {new BlockPoint(0,0,0),new BlockPoint(3,0,0),new BlockPoint(3,0,3),new BlockPoint(0,0,3)};
            if (facing.step() < 0) {
                BlockPoint swap = corners[1]; corners[1] = corners[3]; corners[3] = swap;
            }
            ScreenSelection valid = new ScreenSelection();
            for (BlockPoint corner : corners) { valid.select(world, corner, facing); }
            assertEquals(4, valid.size());
            ScreenSelection reversed = new ScreenSelection();
            reversed.select(world,corners[0],facing); reversed.select(world,corners[3],facing);
            reversed.select(world,corners[2],facing);
            assertThrows(IllegalArgumentException.class, () -> reversed.select(world,corners[1],facing));
            assertEquals(3, reversed.size());
        }
    }

    @Test
    void orderedCornersCreatePreviewAndUndoLeavesServerUntouched() {
        ScreenSelection selection = new ScreenSelection();
        selection.select(world, new BlockPoint(7, 68, 2), Facing.NORTH);
        selection.select(world, new BlockPoint(0, 68, 2), Facing.NORTH);
        selection.select(world, new BlockPoint(0, 64, 2), Facing.NORTH);
        selection.select(world, new BlockPoint(7, 64, 2), Facing.NORTH);
        assertEquals(8, selection.geometry().widthBlocks());
        assertEquals(5, selection.geometry().heightBlocks());
        assertThrows(IllegalStateException.class, () -> selection.select(world, new BlockPoint(3, 65, 2), Facing.NORTH));
        selection.undo();
        assertEquals(3, selection.size());
        assertThrows(IllegalStateException.class, selection::geometry);
        selection.clear();
        assertNull(selection.dimension());
    }

    @Test
    void invalidFourthCornerCanBeCorrectedAndCrossWorldSelectionIsRejected() {
        ScreenSelection selection = new ScreenSelection();
        selection.select(world, new BlockPoint(7, 68, 2), Facing.NORTH);
        selection.select(world, new BlockPoint(0, 68, 2), Facing.NORTH);
        selection.select(world, new BlockPoint(0, 64, 2), Facing.NORTH);
        assertThrows(IllegalArgumentException.class, () -> selection.select(world, new BlockPoint(6, 64, 2), Facing.NORTH));
        assertEquals(3, selection.size());
        assertThrows(IllegalArgumentException.class, () -> selection.select(new DimensionKey("minecraft:the_nether"), new BlockPoint(7, 64, 2), Facing.NORTH));
        selection.select(world, new BlockPoint(7, 64, 2), Facing.NORTH);
        assertEquals(4, selection.size());
    }
}
