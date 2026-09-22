package dev.vidscreen.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import dev.vidscreen.domain.BlockPoint;
import dev.vidscreen.domain.ViewingArea;

/** Platform-neutral line geometry for client-side creation previews. */
public final class SelectionOverlay {
    private static final float AREA_PADDING = 0.002f;
    private static final float MARKER_PADDING = 0.04f;

    private SelectionOverlay() {
    }

    /** Returns the twelve green edges around an inclusive viewing-area block volume. */
    public static List<Line> viewingArea(ViewingArea area) {
        Objects.requireNonNull(area, "area");
        return box(
                area.min().x() - AREA_PADDING,
                area.min().y() - AREA_PADDING,
                area.min().z() - AREA_PADDING,
                area.max().x() + 1 + AREA_PADDING,
                area.max().y() + 1 + AREA_PADDING,
                area.max().z() + 1 + AREA_PADDING,
                0.15f, 1, 0.3f, 0.9f, 3);
    }

    /** Returns one highlighted block-sized wire marker per selected corner. */
    public static List<Line> selectionPoints(List<BlockPoint> points) {
        Objects.requireNonNull(points, "points");
        List<Line> lines = new ArrayList<Line>(points.size() * 12);
        for (int index = 0; index < points.size(); index++) {
            BlockPoint point = Objects.requireNonNull(points.get(index), "point");
            float red = index == 0 ? 1 : 0.1f;
            float green = index == 0 ? 0.75f : 0.85f;
            float blue = index == 0 ? 0.1f : 1;
            lines.addAll(box(
                    point.x() - MARKER_PADDING,
                    point.y() - MARKER_PADDING,
                    point.z() - MARKER_PADDING,
                    point.x() + 1 + MARKER_PADDING,
                    point.y() + 1 + MARKER_PADDING,
                    point.z() + 1 + MARKER_PADDING,
                    red, green, blue, 1, 4));
        }
        return Collections.unmodifiableList(lines);
    }

    private static List<Line> box(
            float x0, float y0, float z0,
            float x1, float y1, float z1,
            float red, float green, float blue, float alpha,
            float width) {
        Point[] corners = new Point[] {
                new Point(x0, y0, z0), new Point(x1, y0, z0),
                new Point(x1, y1, z0), new Point(x0, y1, z0),
                new Point(x0, y0, z1), new Point(x1, y0, z1),
                new Point(x1, y1, z1), new Point(x0, y1, z1)
        };
        int[][] edges = new int[][] {
                {0, 1}, {1, 2}, {2, 3}, {3, 0},
                {4, 5}, {5, 6}, {6, 7}, {7, 4},
                {0, 4}, {1, 5}, {2, 6}, {3, 7}
        };
        List<Line> lines = new ArrayList<Line>(edges.length);
        for (int[] edge : edges) {
            lines.add(new Line(corners[edge[0]], corners[edge[1]], red, green, blue, alpha, width));
        }
        return Collections.unmodifiableList(lines);
    }

    public static final class Point {
        private final float x;
        private final float y;
        private final float z;

        private Point(float x, float y, float z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public float x() { return x; }
        public float y() { return y; }
        public float z() { return z; }
    }

    public static final class Line {
        private final Point start;
        private final Point end;
        private final float red;
        private final float green;
        private final float blue;
        private final float alpha;
        private final float width;

        private Line(
                Point start, Point end,
                float red, float green, float blue, float alpha,
                float width) {
            this.start = start;
            this.end = end;
            this.red = red;
            this.green = green;
            this.blue = blue;
            this.alpha = alpha;
            this.width = width;
        }

        public Point start() { return start; }
        public Point end() { return end; }
        public float red() { return red; }
        public float green() { return green; }
        public float blue() { return blue; }
        public float alpha() { return alpha; }
        public float width() { return width; }
    }
}
