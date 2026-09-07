package dev.vidscreen.domain;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

public final class ScreenDefinition {
    private static final Pattern NAME_PATTERN = Pattern.compile("[a-z0-9][a-z0-9_-]{0,63}");

    private final UUID id;
    private final String name;
    private final DimensionKey dimension;
    private final ScreenGeometry geometry;
    private final ScreenFit fit;
    private final double viewDistance;

    public ScreenDefinition(
            UUID id,
            String name,
            DimensionKey dimension,
            ScreenGeometry geometry,
            ScreenFit fit,
            double viewDistance) {
        this.id = Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        String normalizedName = name.toLowerCase(Locale.ROOT);
        if (!NAME_PATTERN.matcher(normalizedName).matches()) {
            throw new IllegalArgumentException("Invalid screen name: " + name);
        }
        if (normalizedName.getBytes(StandardCharsets.UTF_8).length > VidScreenLimits.MAX_SCREEN_NAME_BYTES) {
            throw new IllegalArgumentException("Screen name is too long");
        }
        if (!Double.isFinite(viewDistance) || viewDistance < 1 || viewDistance > 1_024) {
            throw new IllegalArgumentException("viewDistance must be between 1 and 1024 blocks");
        }
        this.name = normalizedName;
        this.dimension = Objects.requireNonNull(dimension, "dimension");
        this.geometry = Objects.requireNonNull(geometry, "geometry");
        this.fit = Objects.requireNonNull(fit, "fit");
        this.viewDistance = viewDistance;
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public DimensionKey dimension() {
        return dimension;
    }

    public ScreenGeometry geometry() {
        return geometry;
    }

    public ScreenFit fit() {
        return fit;
    }

    public double viewDistance() {
        return viewDistance;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ScreenDefinition)) {
            return false;
        }
        ScreenDefinition that = (ScreenDefinition) other;
        return Double.compare(viewDistance, that.viewDistance) == 0
                && id.equals(that.id)
                && name.equals(that.name)
                && dimension.equals(that.dimension)
                && geometry.equals(that.geometry)
                && fit == that.fit;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, dimension, geometry, fit, viewDistance);
    }
}
