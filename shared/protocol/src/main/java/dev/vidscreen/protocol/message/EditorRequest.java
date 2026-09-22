package dev.vidscreen.protocol.message;

import java.util.Objects;
import java.util.UUID;
import dev.vidscreen.domain.MediaDescriptor;
import dev.vidscreen.domain.ScreenDefinition;
import dev.vidscreen.domain.ViewingArea;
import dev.vidscreen.protocol.MessageType;
import dev.vidscreen.protocol.WireMessage;

/** Typed, permission-checked screen operations. Never carries commands to execute. */
public final class EditorRequest implements WireMessage {
    public enum Action { CREATE, UPDATE, DELETE, SOURCE, PLAY, PAUSE, STOP, SEEK, RATE, LOOP, SYNC,
        AREA_CREATE, AREA_UPDATE, AREA_DELETE }
    private final UUID operationId;
    private final Action action;
    private final UUID screenId;
    private final ScreenDefinition definition;
    private final MediaDescriptor media;
    private final double value;
    private final ViewingArea area;

    public EditorRequest(UUID operationId, Action action, UUID screenId,
            ScreenDefinition definition, MediaDescriptor media, double value) {
        this(operationId, action, screenId, definition, media, value, null);
    }

    public EditorRequest(UUID operationId, Action action, UUID screenId,
            ScreenDefinition definition, MediaDescriptor media, double value, ViewingArea area) {
        this.operationId = Objects.requireNonNull(operationId, "operationId");
        this.action = Objects.requireNonNull(action, "action");
        this.screenId = Objects.requireNonNull(screenId, "screenId");
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("Editor value must be finite");
        }
        if (action == Action.CREATE || action == Action.UPDATE) {
            Objects.requireNonNull(definition, "definition");
            if (!screenId.equals(definition.id())) {
                throw new IllegalArgumentException("Screen ID mismatch");
            }
        } else if (definition != null) {
            throw new IllegalArgumentException("Unexpected screen definition");
        }
        if ((action == Action.SOURCE) != (media != null)) {
            throw new IllegalArgumentException("Source operation requires media only");
        }
        if (action == Action.SEEK && (value < 0 || value > 365.0 * 24 * 3600 * 1000)
                || action == Action.RATE && (value < 0.25 || value > 4)
                || action == Action.LOOP && value != 0 && value != 1) {
            throw new IllegalArgumentException("Editor value out of range");
        }
        this.definition = definition;
        this.media = media;
        this.value = value;
        if (action == Action.AREA_CREATE || action == Action.AREA_UPDATE) {
            Objects.requireNonNull(area, "area");
            if (!screenId.equals(area.id())) { throw new IllegalArgumentException("Area ID mismatch"); }
        } else if (area != null) { throw new IllegalArgumentException("Unexpected viewing area"); }
        this.area = area;
    }

    public static EditorRequest operation(Action action, UUID screenId, double value) {
        return new EditorRequest(UUID.randomUUID(), action, screenId, null, null, value);
    }

    public static EditorRequest definition(Action action, ScreenDefinition definition) {
        return new EditorRequest(UUID.randomUUID(), action, definition.id(), definition, null, 0);
    }

    public static EditorRequest source(UUID screenId, MediaDescriptor media) {
        return new EditorRequest(UUID.randomUUID(), Action.SOURCE, screenId, null, media, 0);
    }

    public static EditorRequest area(Action action, ViewingArea area) {
        return new EditorRequest(UUID.randomUUID(), action, area.id(), null, null, 0, area);
    }

    @Override public MessageType type() { return MessageType.EDITOR_REQUEST; }
    public UUID operationId() { return operationId; }
    public Action action() { return action; }
    public UUID screenId() { return screenId; }
    public ScreenDefinition definition() { return definition; }
    public MediaDescriptor media() { return media; }
    public double value() { return value; }
    public ViewingArea area() { return area; }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof EditorRequest)) { return false; }
        EditorRequest that = (EditorRequest) other;
        return operationId.equals(that.operationId) && action == that.action && screenId.equals(that.screenId)
                && Objects.equals(definition, that.definition) && Objects.equals(media, that.media)
                && Double.compare(value, that.value) == 0 && Objects.equals(area, that.area);
    }

    @Override public int hashCode() { return Objects.hash(operationId, action, screenId, definition, media, value, area); }
}
