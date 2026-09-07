package dev.vidscreen.domain;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public final class DimensionKey {
    private static final Pattern PATTERN = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    private final String value;

    public DimensionKey(String value) {
        Objects.requireNonNull(value, "value");
        String normalized = value.toLowerCase(Locale.ROOT);
        if (!PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Invalid dimension key: " + value);
        }
        if (normalized.length() > VidScreenLimits.MAX_DIMENSION_KEY_BYTES) {
            throw new IllegalArgumentException("Dimension key is too long");
        }
        this.value = normalized;
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof DimensionKey && value.equals(((DimensionKey) other).value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value;
    }
}
