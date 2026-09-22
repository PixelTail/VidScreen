package dev.vidscreen.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import dev.vidscreen.domain.*;

/** Local draft only. Selecting and undoing never mutates the server. */
public final class ScreenSelection {
    private final List<BlockPoint> corners = new ArrayList<BlockPoint>();
    private DimensionKey dimension;
    private Facing facing;

    public void select(DimensionKey world, BlockPoint point, Facing face) {
        if (corners.size() == 4) {
            throw new IllegalStateException("Four corners selected. Confirm in the menu or undo a point.");
        }
        if (dimension != null && !dimension.equals(world)) {
            throw new IllegalArgumentException("All corners must be in the same dimension.");
        }
        if (!corners.isEmpty() && point.coordinate(facing.axis()) != corners.get(0).coordinate(facing.axis())) {
            throw new IllegalArgumentException("All screen corners must lie on the same flat block face.");
        }
        if (corners.contains(point)) {
            throw new IllegalArgumentException("Choose a different corner.");
        }
        corners.add(point);
        dimension = world;
        if (corners.size() == 1) { facing = face; }
        if (corners.size() == 4) {
            try { geometry(); } catch (RuntimeException error) { corners.remove(3); throw error; }
        }
    }

    public void undo() {
        if (!corners.isEmpty()) { corners.remove(corners.size() - 1); }
        if (corners.isEmpty()) { dimension = null; facing = null; }
    }

    public void clear() { corners.clear(); dimension = null; facing = null; }
    public int size() { return corners.size(); }
    public DimensionKey dimension() { return dimension; }
    public Facing facing() { return facing; }
    public List<BlockPoint> corners() { return Collections.unmodifiableList(new ArrayList<BlockPoint>(corners)); }

    public ScreenGeometry geometry() {
        if (corners.size() != 4) { throw new IllegalStateException("Select all four corners first."); }
        BlockPoint a = corners.get(0), b = corners.get(1), c = corners.get(2), d = corners.get(3);
        long[] ab = delta(a, b), bc = delta(b, c);
        int abAxis = singleAxis(ab), bcAxis = singleAxis(bc);
        long cross = facing.axis() == Axis.X ? ab[1] * bc[2] - ab[2] * bc[1]
                : facing.axis() == Axis.Y ? ab[2] * bc[0] - ab[0] * bc[2]
                : ab[0] * bc[1] - ab[1] * bc[0];
        if (cross * facing.step() >= 0) {
            throw new IllegalArgumentException("Select the corners clockwise while facing the screen.");
        }
        if (abAxis == bcAxis || (long) a.x() + c.x() != (long) b.x() + d.x()
                || (long) a.y() + c.y() != (long) b.y() + d.y()
                || (long) a.z() + c.z() != (long) b.z() + d.z()) {
            throw new IllegalArgumentException("Select a rectangle in order: top left, top right, bottom right, bottom left.");
        }
        return ScreenGeometry.between(a, c, facing);
    }

    private static long[] delta(BlockPoint a, BlockPoint b) {
        return new long[] {(long) b.x() - a.x(), (long) b.y() - a.y(), (long) b.z() - a.z()};
    }

    private static int singleAxis(long[] direction) {
        int result = -1;
        for (int i = 0; i < 3; i++) {
            if (direction[i] != 0) {
                if (result != -1) { throw new IllegalArgumentException("Screen edges must follow the block grid."); }
                result = i;
            }
        }
        if (result == -1) { throw new IllegalArgumentException("Screen corners must be distinct."); }
        return result;
    }
}
