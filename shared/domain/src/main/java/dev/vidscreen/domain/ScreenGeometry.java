package dev.vidscreen.domain;

import java.util.Objects;

public final class ScreenGeometry {
    private final BlockPoint min;
    private final BlockPoint max;
    private final Facing facing;

    private ScreenGeometry(BlockPoint min, BlockPoint max, Facing facing) {
        this.min = min;
        this.max = max;
        this.facing = facing;
    }

    public static ScreenGeometry between(BlockPoint first, BlockPoint second, Facing facing) {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        Objects.requireNonNull(facing, "facing");

        if (first.coordinate(facing.axis()) != second.coordinate(facing.axis())) {
            throw new IllegalArgumentException("Screen corners must share the coordinate normal to " + facing);
        }

        BlockPoint min = new BlockPoint(
                Math.min(first.x(), second.x()),
                Math.min(first.y(), second.y()),
                Math.min(first.z(), second.z()));
        BlockPoint max = new BlockPoint(
                Math.max(first.x(), second.x()),
                Math.max(first.y(), second.y()),
                Math.max(first.z(), second.z()));

        ScreenGeometry geometry = new ScreenGeometry(min, max, facing);
        geometry.validateSize();
        return geometry;
    }

    private void validateSize() {
        if (widthBlocks() > VidScreenLimits.MAX_SCREEN_WIDTH_BLOCKS) {
            throw new IllegalArgumentException("Screen width exceeds " + VidScreenLimits.MAX_SCREEN_WIDTH_BLOCKS + " blocks");
        }
        if (heightBlocks() > VidScreenLimits.MAX_SCREEN_HEIGHT_BLOCKS) {
            throw new IllegalArgumentException("Screen height exceeds " + VidScreenLimits.MAX_SCREEN_HEIGHT_BLOCKS + " blocks");
        }
        if (areaBlocks() > VidScreenLimits.MAX_SCREEN_AREA_BLOCKS) {
            throw new IllegalArgumentException("Screen area exceeds " + VidScreenLimits.MAX_SCREEN_AREA_BLOCKS + " blocks");
        }
    }

    public BlockPoint min() {
        return min;
    }

    public BlockPoint max() {
        return max;
    }

    public Facing facing() {
        return facing;
    }

    public int widthBlocks() {
        switch (facing.axis()) {
            case X:
                return max.z() - min.z() + 1;
            case Y:
            case Z:
                return max.x() - min.x() + 1;
            default:
                throw new AssertionError(facing.axis());
        }
    }

    public int heightBlocks() {
        switch (facing.axis()) {
            case X:
            case Z:
                return max.y() - min.y() + 1;
            case Y:
                return max.z() - min.z() + 1;
            default:
                throw new AssertionError(facing.axis());
        }
    }

    public long areaBlocks() {
        return (long) widthBlocks() * heightBlocks();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ScreenGeometry)) {
            return false;
        }
        ScreenGeometry that = (ScreenGeometry) other;
        return min.equals(that.min) && max.equals(that.max) && facing == that.facing;
    }

    @Override
    public int hashCode() {
        return Objects.hash(min, max, facing);
    }

    @Override
    public String toString() {
        return "ScreenGeometry{" + "min=" + min + ", max=" + max + ", facing=" + facing + '}';
    }
}
