package dev.vidscreen.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import dev.vidscreen.domain.BlockPoint;
import dev.vidscreen.domain.Facing;
import dev.vidscreen.domain.ScreenFit;
import dev.vidscreen.domain.ScreenGeometry;
import dev.vidscreen.domain.ScreenStyle;

/**
 * Builds platform-neutral screen quads in world coordinates.
 *
 * <p>Content is represented as a list of quads so loader adapters do not care
 * whether a screen is flat or horizontally curved.</p>
 */
public final class ScreenMesh {
    public static final int VIDEO_TEXTURE_WIDTH = 640;
    public static final int VIDEO_TEXTURE_HEIGHT = 360;

    private static final float SURFACE_OFFSET = 0.002f;
    private static final double CURVE_EPSILON_RADIANS = 1.0e-8;

    private ScreenMesh() {
    }

    /** Returns the full flat surface as one quad. */
    public static Quad surface(ScreenGeometry geometry) {
        return surface(geometry, ScreenStyle.FLAT).get(0);
    }

    /** Returns the full styled surface, subdivided when it is curved. */
    public static List<Quad> surface(ScreenGeometry geometry, ScreenStyle style) {
        return Surface.of(geometry, style).quads(0, 1, 0, 1, 0, 1, 0, 1);
    }

    /** Returns the physical center used for distance and anchor-chunk decisions. */
    public static Vertex midpoint(ScreenGeometry geometry, ScreenStyle style) {
        return Surface.of(geometry, style).vertex(0.5f, 0.5f, 0.5f, 0.5f);
    }

    /** Lays out the fixed-size video texture currently produced by the media players. */
    public static Layout video(ScreenGeometry geometry, ScreenFit fit, ScreenStyle style) {
        return layout(geometry, fit, style, VIDEO_TEXTURE_WIDTH, VIDEO_TEXTURE_HEIGHT);
    }

    public static Layout video(ScreenGeometry geometry, ScreenFit fit) {
        return video(geometry, fit, ScreenStyle.FLAT);
    }

    public static Layout layout(
            ScreenGeometry geometry,
            ScreenFit fit,
            int textureWidth,
            int textureHeight) {
        return layout(geometry, fit, ScreenStyle.FLAT, textureWidth, textureHeight);
    }

    /** Lays out a texture on a styled screen while preserving the requested fit semantics. */
    public static Layout layout(
            ScreenGeometry geometry,
            ScreenFit fit,
            ScreenStyle style,
            int textureWidth,
            int textureHeight) {
        Objects.requireNonNull(geometry, "geometry");
        Objects.requireNonNull(fit, "fit");
        Objects.requireNonNull(style, "style");
        if (textureWidth < 1 || textureHeight < 1) {
            throw new IllegalArgumentException("Texture dimensions must be positive");
        }

        Surface surface = Surface.of(geometry, style);
        double screenAspect = surface.surfaceWidth() / geometry.heightBlocks();
        double textureAspect = (double) textureWidth / textureHeight;

        switch (fit) {
            case STRETCH:
                return contentOnly(surface.quads(0, 1, 0, 1, 0, 1, 0, 1));
            case COVER:
                return cover(surface, screenAspect, textureAspect);
            case CONTAIN:
                return contain(surface, screenAspect, textureAspect);
            default:
                throw new AssertionError(fit);
        }
    }

    private static Layout cover(Surface surface, double screenAspect, double textureAspect) {
        float u0 = 0;
        float u1 = 1;
        float v0 = 0;
        float v1 = 1;
        if (screenAspect > textureAspect) {
            float visibleHeight = (float) (textureAspect / screenAspect);
            v0 = (1 - visibleHeight) / 2;
            v1 = 1 - v0;
        } else if (screenAspect < textureAspect) {
            float visibleWidth = (float) (screenAspect / textureAspect);
            u0 = (1 - visibleWidth) / 2;
            u1 = 1 - u0;
        }
        return contentOnly(surface.quads(0, 1, 0, 1, u0, u1, v0, v1));
    }

    private static Layout contain(Surface surface, double screenAspect, double textureAspect) {
        float s0 = 0;
        float s1 = 1;
        float t0 = 0;
        float t1 = 1;
        List<Quad> background = new ArrayList<Quad>();
        if (screenAspect > textureAspect) {
            float contentWidth = (float) (textureAspect / screenAspect);
            s0 = (1 - contentWidth) / 2;
            s1 = 1 - s0;
            background.addAll(surface.quads(0, s0, 0, 1, 0, 1, 0, 1));
            background.addAll(surface.quads(s1, 1, 0, 1, 0, 1, 0, 1));
        } else if (screenAspect < textureAspect) {
            float contentHeight = (float) (screenAspect / textureAspect);
            t0 = (1 - contentHeight) / 2;
            t1 = 1 - t0;
            background.addAll(surface.quads(0, 1, 0, t0, 0, 1, 0, 1));
            background.addAll(surface.quads(0, 1, t1, 1, 0, 1, 0, 1));
        }

        List<Quad> content = surface.quads(s0, s1, t0, t1, 0, 1, 0, 1);
        return new Layout(content, background);
    }

    private static Layout contentOnly(List<Quad> content) {
        return new Layout(content, Collections.<Quad>emptyList());
    }

    public static final class Layout {
        private final List<Quad> contentQuads;
        private final List<Quad> backgroundQuads;

        private Layout(List<Quad> contentQuads, List<Quad> backgroundQuads) {
            this.contentQuads = Collections.unmodifiableList(new ArrayList<Quad>(contentQuads));
            this.backgroundQuads = Collections.unmodifiableList(new ArrayList<Quad>(backgroundQuads));
        }

        public List<Quad> contentQuads() { return contentQuads; }
        public List<Quad> backgroundQuads() { return backgroundQuads; }
    }

    public static final class Quad {
        private final Vertex[] vertices;

        private Quad(Vertex first, Vertex second, Vertex third, Vertex fourth) {
            this.vertices = new Vertex[] {first, second, third, fourth};
        }

        public int vertexCount() { return vertices.length; }
        public Vertex vertex(int index) { return vertices[index]; }

        public void emit(VertexSink sink) {
            Objects.requireNonNull(sink, "sink");
            for (Vertex vertex : vertices) {
                sink.add(
                        vertex.x(), vertex.y(), vertex.z(),
                        vertex.u(), vertex.v(),
                        vertex.normalX(), vertex.normalY(), vertex.normalZ());
            }
        }
    }

    public static final class Vertex {
        private final float x;
        private final float y;
        private final float z;
        private final float u;
        private final float v;
        private final float normalX;
        private final float normalY;
        private final float normalZ;

        private Vertex(
                float x, float y, float z,
                float u, float v,
                float normalX, float normalY, float normalZ) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.u = u;
            this.v = v;
            this.normalX = normalX;
            this.normalY = normalY;
            this.normalZ = normalZ;
        }

        public float x() { return x; }
        public float y() { return y; }
        public float z() { return z; }
        public float u() { return u; }
        public float v() { return v; }
        public float normalX() { return normalX; }
        public float normalY() { return normalY; }
        public float normalZ() { return normalZ; }
    }

    @FunctionalInterface
    public interface VertexSink {
        void add(float x, float y, float z, float u, float v, float normalX, float normalY, float normalZ);
    }

    private static final class Surface {
        private final float originX;
        private final float originY;
        private final float originZ;
        private final float rightUnitX;
        private final float rightUnitY;
        private final float rightUnitZ;
        private final float upX;
        private final float upY;
        private final float upZ;
        private final float baseNormalX;
        private final float baseNormalY;
        private final float baseNormalZ;
        private final float width;
        private final int segments;
        private final double curveRadians;
        private final double curveSign;
        private final double radius;

        private Surface(
                float originX, float originY, float originZ,
                float rightUnitX, float rightUnitY, float rightUnitZ,
                float upX, float upY, float upZ,
                float baseNormalX, float baseNormalY, float baseNormalZ,
                float width, int segments, double curvatureDegrees) {
            this.originX = originX;
            this.originY = originY;
            this.originZ = originZ;
            this.rightUnitX = rightUnitX;
            this.rightUnitY = rightUnitY;
            this.rightUnitZ = rightUnitZ;
            this.upX = upX;
            this.upY = upY;
            this.upZ = upZ;
            this.baseNormalX = baseNormalX;
            this.baseNormalY = baseNormalY;
            this.baseNormalZ = baseNormalZ;
            this.width = width;
            this.curveRadians = Math.abs(Math.toRadians(curvatureDegrees));
            this.curveSign = Math.signum(curvatureDegrees);
            if (curveRadians > CURVE_EPSILON_RADIANS) {
                this.segments = segments;
                this.radius = width / (2 * Math.sin(curveRadians / 2));
            } else {
                this.segments = 1;
                this.radius = Double.POSITIVE_INFINITY;
            }
        }

        private static Surface of(ScreenGeometry geometry, ScreenStyle style) {
            Objects.requireNonNull(geometry, "geometry");
            Objects.requireNonNull(style, "style");
            BlockPoint min = geometry.min();
            BlockPoint max = geometry.max();
            float x0 = min.x();
            float y0 = min.y();
            float z0 = min.z();
            float x1 = max.x() + 1;
            float y1 = max.y() + 1;
            float z1 = max.z() + 1;
            float offsetX = (float) style.offsetX();
            float offsetY = (float) style.offsetY();
            float offsetZ = (float) style.offsetZ();
            float width = geometry.widthBlocks();
            Facing facing = geometry.facing();
            switch (facing) {
                case NORTH:
                    return new Surface(
                            x1 + offsetX, y0 + offsetY, z0 - SURFACE_OFFSET + offsetZ,
                            -1, 0, 0, 0, y1 - y0, 0, 0, 0, -1,
                            width, style.segments(), style.curvatureDegrees());
                case SOUTH:
                    return new Surface(
                            x0 + offsetX, y0 + offsetY, z1 + SURFACE_OFFSET + offsetZ,
                            1, 0, 0, 0, y1 - y0, 0, 0, 0, 1,
                            width, style.segments(), style.curvatureDegrees());
                case WEST:
                    return new Surface(
                            x0 - SURFACE_OFFSET + offsetX, y0 + offsetY, z0 + offsetZ,
                            0, 0, 1, 0, y1 - y0, 0, -1, 0, 0,
                            width, style.segments(), style.curvatureDegrees());
                case EAST:
                    return new Surface(
                            x1 + SURFACE_OFFSET + offsetX, y0 + offsetY, z1 + offsetZ,
                            0, 0, -1, 0, y1 - y0, 0, 1, 0, 0,
                            width, style.segments(), style.curvatureDegrees());
                case DOWN:
                    return new Surface(
                            x0 + offsetX, y0 - SURFACE_OFFSET + offsetY, z0 + offsetZ,
                            1, 0, 0, 0, 0, z1 - z0, 0, -1, 0,
                            width, style.segments(), style.curvatureDegrees());
                case UP:
                    return new Surface(
                            x0 + offsetX, y1 + SURFACE_OFFSET + offsetY, z1 + offsetZ,
                            1, 0, 0, 0, 0, z0 - z1, 0, 1, 0,
                            width, style.segments(), style.curvatureDegrees());
                default:
                    throw new AssertionError(facing);
            }
        }

        private double surfaceWidth() {
            return curveRadians <= CURVE_EPSILON_RADIANS ? width : radius * curveRadians;
        }

        private List<Quad> quads(
                float s0, float s1, float t0, float t1,
                float u0, float u1, float v0, float v1) {
            List<Quad> result = new ArrayList<Quad>(segments);
            for (int index = 0; index < segments; index++) {
                float fraction0 = (float) index / segments;
                float fraction1 = (float) (index + 1) / segments;
                float segmentS0 = interpolate(s0, s1, fraction0);
                float segmentS1 = interpolate(s0, s1, fraction1);
                float segmentU0 = interpolate(u0, u1, fraction0);
                float segmentU1 = interpolate(u0, u1, fraction1);
                result.add(new Quad(
                        vertex(segmentS0, t0, segmentU0, v1),
                        vertex(segmentS1, t0, segmentU1, v1),
                        vertex(segmentS1, t1, segmentU1, v0),
                        vertex(segmentS0, t1, segmentU0, v0)));
            }
            return Collections.unmodifiableList(result);
        }

        private Vertex vertex(float s, float t, float u, float v) {
            double along;
            double depth;
            double normalRight;
            double normalOutward;
            if (curveRadians <= CURVE_EPSILON_RADIANS) {
                along = width * s;
                depth = 0;
                normalRight = 0;
                normalOutward = 1;
            } else {
                double phi = (s - 0.5) * curveRadians;
                along = width / 2.0 + radius * Math.sin(phi);
                depth = curveSign * (radius * Math.cos(curveRadians / 2) - radius * Math.cos(phi));
                normalRight = -curveSign * Math.sin(phi);
                normalOutward = Math.cos(phi);
            }
            return new Vertex(
                    (float) (originX + rightUnitX * along + baseNormalX * depth + upX * t),
                    (float) (originY + rightUnitY * along + baseNormalY * depth + upY * t),
                    (float) (originZ + rightUnitZ * along + baseNormalZ * depth + upZ * t),
                    u, v,
                    (float) (rightUnitX * normalRight + baseNormalX * normalOutward),
                    (float) (rightUnitY * normalRight + baseNormalY * normalOutward),
                    (float) (rightUnitZ * normalRight + baseNormalZ * normalOutward));
        }

        private static float interpolate(float start, float end, float fraction) {
            return start + (end - start) * fraction;
        }
    }
}
