package dev.vidscreen.domain;

public enum Facing {
    NORTH(Axis.Z, -1),
    SOUTH(Axis.Z, 1),
    WEST(Axis.X, -1),
    EAST(Axis.X, 1),
    DOWN(Axis.Y, -1),
    UP(Axis.Y, 1);

    private final Axis axis;
    private final int step;

    Facing(Axis axis, int step) {
        this.axis = axis;
        this.step = step;
    }

    public Axis axis() {
        return axis;
    }

    public int step() {
        return step;
    }

    public Facing opposite() {
        switch (this) {
            case NORTH:
                return SOUTH;
            case SOUTH:
                return NORTH;
            case WEST:
                return EAST;
            case EAST:
                return WEST;
            case DOWN:
                return UP;
            case UP:
                return DOWN;
            default:
                throw new AssertionError(this);
        }
    }
}
