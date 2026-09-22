package dev.vidscreen.domain;

import java.util.Objects;

/** A bounded horizontal cylindrical curve and translation, relative to the selected block face. */
public final class ScreenStyle {
    public static final ScreenStyle FLAT = new ScreenStyle(0, 32, 0, 0, 0);
    private final double curvatureDegrees;
    private final int segments;
    private final double offsetX, offsetY, offsetZ;

    public ScreenStyle(double curvatureDegrees, int segments, double offsetX, double offsetY, double offsetZ) {
        if (!Double.isFinite(curvatureDegrees) || Math.abs(curvatureDegrees) > 170
                || segments < 1 || segments > 128 || !offsetValid(offsetX) || !offsetValid(offsetY) || !offsetValid(offsetZ)) {
            throw new IllegalArgumentException("Invalid screen curve, subdivisions or translation");
        }
        this.curvatureDegrees = curvatureDegrees; this.segments = segments;
        this.offsetX = offsetX; this.offsetY = offsetY; this.offsetZ = offsetZ;
    }
    private static boolean offsetValid(double value) { return Double.isFinite(value) && Math.abs(value) <= 256; }
    public double curvatureDegrees() { return curvatureDegrees; }
    public int segments() { return segments; }
    public double offsetX() { return offsetX; }
    public double offsetY() { return offsetY; }
    public double offsetZ() { return offsetZ; }
    @Override public boolean equals(Object other) {
        if (!(other instanceof ScreenStyle)) { return false; }
        ScreenStyle that = (ScreenStyle) other;
        return Double.compare(curvatureDegrees, that.curvatureDegrees) == 0 && segments == that.segments
                && Double.compare(offsetX, that.offsetX) == 0 && Double.compare(offsetY, that.offsetY) == 0
                && Double.compare(offsetZ, that.offsetZ) == 0;
    }
    @Override public int hashCode() { return Objects.hash(curvatureDegrees, segments, offsetX, offsetY, offsetZ); }
}
