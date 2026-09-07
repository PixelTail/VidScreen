package dev.vidscreen.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ScreenGeometryTest {
    @Test
    void createsNorthFacingScreenRegardlessOfCornerOrder() {
        ScreenGeometry first = ScreenGeometry.between(
                new BlockPoint(10, 64, 20),
                new BlockPoint(13, 66, 20),
                Facing.NORTH);
        ScreenGeometry reversed = ScreenGeometry.between(
                new BlockPoint(13, 66, 20),
                new BlockPoint(10, 64, 20),
                Facing.NORTH);

        assertEquals(first, reversed);
        assertEquals(4, first.widthBlocks());
        assertEquals(3, first.heightBlocks());
        assertEquals(12, first.areaBlocks());
    }

    @Test
    void supportsOneByOneScreenUsingFacingToChoosePlane() {
        ScreenGeometry geometry = ScreenGeometry.between(
                new BlockPoint(1, 2, 3),
                new BlockPoint(1, 2, 3),
                Facing.EAST);

        assertEquals(1, geometry.widthBlocks());
        assertEquals(1, geometry.heightBlocks());
    }

    @Test
    void rejectsCornersThatDoNotShareFacingPlane() {
        assertThrows(IllegalArgumentException.class, () -> ScreenGeometry.between(
                new BlockPoint(0, 0, 0),
                new BlockPoint(0, 0, 1),
                Facing.NORTH));
    }

    @Test
    void rejectsOversizedScreens() {
        assertThrows(IllegalArgumentException.class, () -> ScreenGeometry.between(
                new BlockPoint(0, 0, 0),
                new BlockPoint(VidScreenLimits.MAX_SCREEN_WIDTH_BLOCKS, 1, 0),
                Facing.NORTH));
    }
}
