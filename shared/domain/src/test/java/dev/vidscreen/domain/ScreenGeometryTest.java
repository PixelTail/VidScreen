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

    @Test
    void centersOddDimensionsAroundTheSelectedBlock() {
        ScreenGeometry geometry = ScreenGeometry.centered(
                new BlockPoint(10, 64, 20), Facing.NORTH, 5, 3, 0);

        assertEquals(new BlockPoint(8, 63, 20), geometry.min());
        assertEquals(new BlockPoint(12, 65, 20), geometry.max());
        assertEquals(5, geometry.widthBlocks());
        assertEquals(3, geometry.heightBlocks());
    }

    @Test
    void appliesVerticalOffsetAndPositiveAxisRemainderForEvenDimensions() {
        ScreenGeometry geometry = ScreenGeometry.centered(
                new BlockPoint(10, 64, 20), Facing.EAST, 4, 2, 3);

        assertEquals(new BlockPoint(10, 67, 19), geometry.min());
        assertEquals(new BlockPoint(10, 68, 22), geometry.max());
    }

    @Test
    void centersHorizontalFacingScreensOnTheirWidthPlane() {
        ScreenGeometry geometry = ScreenGeometry.centered(
                new BlockPoint(10, 64, 20), Facing.UP, 3, 5, -2);

        assertEquals(new BlockPoint(9, 62, 18), geometry.min());
        assertEquals(new BlockPoint(11, 62, 22), geometry.max());
    }

    @Test
    void rejectsNonPositiveCenteredDimensions() {
        assertThrows(IllegalArgumentException.class, () -> ScreenGeometry.centered(
                new BlockPoint(0, 0, 0), Facing.NORTH, 0, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> ScreenGeometry.centered(
                new BlockPoint(0, 0, 0), Facing.NORTH, 1, 0, 0));
    }
}
