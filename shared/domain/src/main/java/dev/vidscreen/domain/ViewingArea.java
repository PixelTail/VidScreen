package dev.vidscreen.domain;

import java.util.Objects;
import java.util.UUID;

/** Inclusive selected blocks define the viewing volume, independent of the screen surface. */
public final class ViewingArea {
    public static final int MAX_AREAS = 64;
    private final UUID id;
    private final String name;
    private final DimensionKey dimension;
    private final BlockPoint min, max;

    public ViewingArea(UUID id, String name, DimensionKey dimension, BlockPoint first, BlockPoint second) {
        this.id = Objects.requireNonNull(id, "id");
        this.dimension = Objects.requireNonNull(dimension, "dimension");
        // Use the existing canonical name constraints without introducing a second name grammar.
        this.name = new ScreenDefinition(id, name, dimension, ScreenGeometry.between(first, first, Facing.NORTH),
                ScreenFit.CONTAIN, 96).name();
        min = new BlockPoint(Math.min(first.x(), second.x()), Math.min(first.y(), second.y()), Math.min(first.z(), second.z()));
        max = new BlockPoint(Math.max(first.x(), second.x()), Math.max(first.y(), second.y()), Math.max(first.z(), second.z()));
        if ((long) max.x() - min.x() > 1023 || (long) max.y() - min.y() > 1023 || (long) max.z() - min.z() > 1023) {
            throw new IllegalArgumentException("Viewing area cannot exceed 1024 blocks on any axis");
        }
    }
    public UUID id() { return id; }
    public String name() { return name; }
    public DimensionKey dimension() { return dimension; }
    public BlockPoint min() { return min; }
    public BlockPoint max() { return max; }
    public boolean contains(String world, double x, double y, double z) {
        return dimension.value().equals(world) && x >= min.x() && x < (double) max.x() + 1
                && y >= min.y() && y < (double) max.y() + 1 && z >= min.z() && z < (double) max.z() + 1;
    }
    @Override public boolean equals(Object other) {
        if (!(other instanceof ViewingArea)) { return false; }
        ViewingArea that = (ViewingArea) other;
        return id.equals(that.id) && name.equals(that.name) && dimension.equals(that.dimension)
                && min.equals(that.min) && max.equals(that.max);
    }
    @Override public int hashCode() { return Objects.hash(id, name, dimension, min, max); }
}
