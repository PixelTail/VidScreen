package dev.vidscreen.domain;

import java.util.Objects;

public final class BlockPoint {
    private final int x;
    private final int y;
    private final int z;

    public BlockPoint(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public int z() {
        return z;
    }

    public int coordinate(Axis axis) {
        switch (axis) {
            case X:
                return x;
            case Y:
                return y;
            case Z:
                return z;
            default:
                throw new AssertionError(axis);
        }
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof BlockPoint)) {
            return false;
        }
        BlockPoint that = (BlockPoint) other;
        return x == that.x && y == that.y && z == that.z;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y, z);
    }

    @Override
    public String toString() {
        return "BlockPoint{" + "x=" + x + ", y=" + y + ", z=" + z + '}';
    }
}
