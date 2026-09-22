package dev.vidscreen.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import dev.vidscreen.domain.BlockPoint;
import dev.vidscreen.domain.Facing;
import dev.vidscreen.domain.ScreenFit;
import dev.vidscreen.domain.ScreenGeometry;
import dev.vidscreen.domain.ScreenStyle;

class ScreenMeshTest {
    private static final float EPSILON = 0.0001f;

    @Test
    void emitsOutwardWindingNormalsAndUvForEveryFacing() {
        Map<Facing, float[]> expectedNormals = new EnumMap<Facing, float[]>(Facing.class);
        expectedNormals.put(Facing.NORTH, new float[] {0, 0, -1});
        expectedNormals.put(Facing.SOUTH, new float[] {0, 0, 1});
        expectedNormals.put(Facing.WEST, new float[] {-1, 0, 0});
        expectedNormals.put(Facing.EAST, new float[] {1, 0, 0});
        expectedNormals.put(Facing.DOWN, new float[] {0, -1, 0});
        expectedNormals.put(Facing.UP, new float[] {0, 1, 0});

        for (Facing facing : Facing.values()) {
            ScreenMesh.Quad quad = ScreenMesh.surface(geometry(facing));
            float[] normal = expectedNormals.get(facing);

            assertEquals(4, quad.vertexCount(), facing.name());
            assertOutward(quad, normal, facing.name());
            for (int index = 0; index < quad.vertexCount(); index++) {
                ScreenMesh.Vertex vertex = quad.vertex(index);
                assertEquals(normal[0], vertex.normalX(), EPSILON, facing.name());
                assertEquals(normal[1], vertex.normalY(), EPSILON, facing.name());
                assertEquals(normal[2], vertex.normalZ(), EPSILON, facing.name());
            }

            assertUv(quad.vertex(0), 0, 1, facing.name());
            assertUv(quad.vertex(1), 1, 1, facing.name());
            assertUv(quad.vertex(2), 1, 0, facing.name());
            assertUv(quad.vertex(3), 0, 0, facing.name());
        }
    }

    @Test
    void curvesEveryFacingWithSmoothOutwardNormalsAndContinuousUv() {
        ScreenStyle style = new ScreenStyle(90, 4, 0, 0, 0);
        for (Facing facing : Facing.values()) {
            java.util.List<ScreenMesh.Quad> quads = ScreenMesh.surface(geometry(facing), style);
            ScreenMesh.Quad flat = ScreenMesh.surface(geometry(facing));

            assertEquals(4, quads.size(), facing.name());
            assertPosition(flat.vertex(0), quads.get(0).vertex(0), facing.name() + " first endpoint");
            assertPosition(flat.vertex(1), quads.get(quads.size() - 1).vertex(1), facing.name() + " second endpoint");
            assertPosition(flat.vertex(3), quads.get(0).vertex(3), facing.name() + " first top endpoint");
            assertPosition(flat.vertex(2), quads.get(quads.size() - 1).vertex(2), facing.name() + " second top endpoint");
            for (ScreenMesh.Quad quad : quads) {
                assertWindingMatchesNormals(quad, facing.name());
                for (int vertex = 0; vertex < quad.vertexCount(); vertex++) {
                    ScreenMesh.Vertex point = quad.vertex(vertex);
                    float length = (float) Math.sqrt(
                            point.normalX() * point.normalX()
                                    + point.normalY() * point.normalY()
                                    + point.normalZ() * point.normalZ());
                    assertEquals(1, length, EPSILON, facing.name() + " normal length");
                }
            }

            assertEquals(0, quads.get(0).vertex(0).u(), EPSILON, facing.name());
            assertEquals(1, quads.get(quads.size() - 1).vertex(1).u(), EPSILON, facing.name());
            for (int index = 1; index < quads.size(); index++) {
                assertEquals(
                        quads.get(index - 1).vertex(1).u(),
                        quads.get(index).vertex(0).u(),
                        EPSILON,
                        facing.name() + " uv seam");
            }
        }
    }

    @Test
    void signedCurvatureMovesCenterBehindOrAheadOfSelectedChord() {
        ScreenGeometry geometry = ScreenGeometry.between(
                new BlockPoint(0, 0, 0),
                new BlockPoint(3, 1, 0),
                Facing.NORTH);
        ScreenMesh.Vertex positiveCenter = ScreenMesh.surface(
                geometry, new ScreenStyle(90, 4, 0, 0, 0)).get(1).vertex(1);
        ScreenMesh.Vertex negativeCenter = ScreenMesh.surface(
                geometry, new ScreenStyle(-90, 4, 0, 0, 0)).get(1).vertex(1);
        float flatZ = ScreenMesh.surface(geometry).vertex(0).z();

        assertTrue(positiveCenter.z() > flatZ, "positive curvature must recede from the north-facing viewer");
        assertTrue(negativeCenter.z() < flatZ, "negative curvature must advance toward the north-facing viewer");
    }

    @Test
    void appliesWorldTranslationWithoutMovingCurveEndpointsRelativeToEachOther() {
        ScreenGeometry geometry = square();
        java.util.List<ScreenMesh.Quad> base = ScreenMesh.surface(
                geometry, new ScreenStyle(60, 3, 0, 0, 0));
        java.util.List<ScreenMesh.Quad> moved = ScreenMesh.surface(
                geometry, new ScreenStyle(60, 3, 2.5, -1.25, 4));

        for (int quad = 0; quad < base.size(); quad++) {
            for (int vertex = 0; vertex < 4; vertex++) {
                assertEquals(base.get(quad).vertex(vertex).x() + 2.5f,
                        moved.get(quad).vertex(vertex).x(), EPSILON);
                assertEquals(base.get(quad).vertex(vertex).y() - 1.25f,
                        moved.get(quad).vertex(vertex).y(), EPSILON);
                assertEquals(base.get(quad).vertex(vertex).z() + 4,
                        moved.get(quad).vertex(vertex).z(), EPSILON);
            }
        }
    }

    @Test
    void containPreservesAspectAndReturnsLetterboxBackground() {
        ScreenMesh.Layout layout = ScreenMesh.layout(square(), ScreenFit.CONTAIN, 16, 9);

        assertEquals(1, layout.contentQuads().size());
        assertEquals(2, layout.backgroundQuads().size());
        ScreenMesh.Quad content = layout.contentQuads().get(0);
        assertEquals(0.875f, minimumY(content), EPSILON);
        assertEquals(3.125f, maximumY(content), EPSILON);
        assertEquals(0, minimumX(content), EPSILON);
        assertEquals(4, maximumX(content), EPSILON);
        assertUv(content.vertex(0), 0, 1, "contain");
        assertUv(content.vertex(2), 1, 0, "contain");

        for (ScreenMesh.Quad background : layout.backgroundQuads()) {
            assertOutward(background, new float[] {0, 0, -1}, "contain background");
        }
    }

    @Test
    void containReturnsPillarboxBackgroundForWideScreen() {
        ScreenGeometry wide = ScreenGeometry.between(
                new BlockPoint(0, 0, 0),
                new BlockPoint(3, 1, 0),
                Facing.NORTH);
        ScreenMesh.Layout layout = ScreenMesh.layout(wide, ScreenFit.CONTAIN, 16, 9);

        ScreenMesh.Quad content = layout.contentQuads().get(0);
        assertEquals(2, layout.backgroundQuads().size());
        assertEquals(0.222222f, minimumX(content), EPSILON);
        assertEquals(3.777778f, maximumX(content), EPSILON);
        assertEquals(0, minimumY(content), EPSILON);
        assertEquals(2, maximumY(content), EPSILON);
    }

    @Test
    void stretchFillsTheSurfaceWithoutCroppingOrBackground() {
        ScreenMesh.Layout layout = ScreenMesh.layout(square(), ScreenFit.STRETCH, 16, 9);

        assertEquals(1, layout.contentQuads().size());
        assertEquals(0, layout.backgroundQuads().size());
        ScreenMesh.Quad content = layout.contentQuads().get(0);
        assertEquals(0, minimumX(content), EPSILON);
        assertEquals(4, maximumX(content), EPSILON);
        assertEquals(0, minimumY(content), EPSILON);
        assertEquals(4, maximumY(content), EPSILON);
        assertUv(content.vertex(0), 0, 1, "stretch");
        assertUv(content.vertex(2), 1, 0, "stretch");
    }

    @Test
    void coverFillsTheSurfaceAndCropsTextureAroundItsCenter() {
        ScreenMesh.Layout layout = ScreenMesh.layout(square(), ScreenFit.COVER, 16, 9);

        assertEquals(1, layout.contentQuads().size());
        assertEquals(0, layout.backgroundQuads().size());
        ScreenMesh.Quad content = layout.contentQuads().get(0);
        assertEquals(0, minimumX(content), EPSILON);
        assertEquals(4, maximumX(content), EPSILON);
        assertEquals(0, minimumY(content), EPSILON);
        assertEquals(4, maximumY(content), EPSILON);
        assertUv(content.vertex(0), 0.21875f, 1, "cover");
        assertUv(content.vertex(2), 0.78125f, 0, "cover");
    }

    @Test
    void rejectsInvalidTextureDimensions() {
        assertThrows(IllegalArgumentException.class,
                () -> ScreenMesh.layout(square(), ScreenFit.CONTAIN, 0, 360));
        assertThrows(IllegalArgumentException.class,
                () -> ScreenMesh.layout(square(), ScreenFit.CONTAIN, 640, -1));
    }

    private static ScreenGeometry square() {
        return ScreenGeometry.between(
                new BlockPoint(0, 0, 0),
                new BlockPoint(3, 3, 0),
                Facing.NORTH);
    }

    private static ScreenGeometry geometry(Facing facing) {
        switch (facing.axis()) {
            case X:
                return ScreenGeometry.between(
                        new BlockPoint(10, 20, 30),
                        new BlockPoint(10, 21, 32),
                        facing);
            case Y:
                return ScreenGeometry.between(
                        new BlockPoint(10, 20, 30),
                        new BlockPoint(12, 20, 31),
                        facing);
            case Z:
                return ScreenGeometry.between(
                        new BlockPoint(10, 20, 30),
                        new BlockPoint(12, 21, 30),
                        facing);
            default:
                throw new AssertionError(facing.axis());
        }
    }

    private static void assertOutward(ScreenMesh.Quad quad, float[] normal, String message) {
        ScreenMesh.Vertex first = quad.vertex(0);
        ScreenMesh.Vertex second = quad.vertex(1);
        ScreenMesh.Vertex third = quad.vertex(2);
        float abX = second.x() - first.x();
        float abY = second.y() - first.y();
        float abZ = second.z() - first.z();
        float acX = third.x() - first.x();
        float acY = third.y() - first.y();
        float acZ = third.z() - first.z();
        float crossX = abY * acZ - abZ * acY;
        float crossY = abZ * acX - abX * acZ;
        float crossZ = abX * acY - abY * acX;
        float dot = crossX * normal[0] + crossY * normal[1] + crossZ * normal[2];
        assertTrue(dot > 0, message + " winding");
    }

    private static void assertWindingMatchesNormals(ScreenMesh.Quad quad, String message) {
        ScreenMesh.Vertex first = quad.vertex(0);
        ScreenMesh.Vertex second = quad.vertex(1);
        ScreenMesh.Vertex third = quad.vertex(2);
        float abX = second.x() - first.x();
        float abY = second.y() - first.y();
        float abZ = second.z() - first.z();
        float acX = third.x() - first.x();
        float acY = third.y() - first.y();
        float acZ = third.z() - first.z();
        float crossX = abY * acZ - abZ * acY;
        float crossY = abZ * acX - abX * acZ;
        float crossZ = abX * acY - abY * acX;
        float normalX = 0;
        float normalY = 0;
        float normalZ = 0;
        for (int index = 0; index < quad.vertexCount(); index++) {
            normalX += quad.vertex(index).normalX();
            normalY += quad.vertex(index).normalY();
            normalZ += quad.vertex(index).normalZ();
        }
        float dot = crossX * normalX + crossY * normalY + crossZ * normalZ;
        assertTrue(dot > 0, message + " curved winding");
    }

    private static void assertUv(ScreenMesh.Vertex vertex, float u, float v, String message) {
        assertEquals(u, vertex.u(), EPSILON, message + " u");
        assertEquals(v, vertex.v(), EPSILON, message + " v");
    }

    private static void assertPosition(ScreenMesh.Vertex expected, ScreenMesh.Vertex actual, String message) {
        assertEquals(expected.x(), actual.x(), EPSILON, message + " x");
        assertEquals(expected.y(), actual.y(), EPSILON, message + " y");
        assertEquals(expected.z(), actual.z(), EPSILON, message + " z");
    }

    private static float minimumX(ScreenMesh.Quad quad) {
        float result = Float.POSITIVE_INFINITY;
        for (int index = 0; index < quad.vertexCount(); index++) {
            result = Math.min(result, quad.vertex(index).x());
        }
        return result;
    }

    private static float maximumX(ScreenMesh.Quad quad) {
        float result = Float.NEGATIVE_INFINITY;
        for (int index = 0; index < quad.vertexCount(); index++) {
            result = Math.max(result, quad.vertex(index).x());
        }
        return result;
    }

    private static float minimumY(ScreenMesh.Quad quad) {
        float result = Float.POSITIVE_INFINITY;
        for (int index = 0; index < quad.vertexCount(); index++) {
            result = Math.min(result, quad.vertex(index).y());
        }
        return result;
    }

    private static float maximumY(ScreenMesh.Quad quad) {
        float result = Float.NEGATIVE_INFINITY;
        for (int index = 0; index < quad.vertexCount(); index++) {
            result = Math.max(result, quad.vertex(index).y());
        }
        return result;
    }
}
