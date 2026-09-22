package dev.vidscreen.server;

import java.io.IOException;
import java.util.Objects;
import java.util.UUID;
import java.util.function.LongSupplier;

import dev.vidscreen.domain.DimensionKey;
import dev.vidscreen.domain.MediaSources;
import dev.vidscreen.domain.PlaybackStatus;
import dev.vidscreen.domain.ScreenDefinition;
import dev.vidscreen.domain.ScreenState;
import dev.vidscreen.domain.ViewingArea;
import dev.vidscreen.protocol.message.EditorRequest;

/**
 * Applies typed client editor requests after the platform has performed its
 * connection and permission checks.  It contains no Minecraft or network
 * code and never executes text commands.
 */
public final class ScreenEditorService {
    private final ScreenService screens;
    private final LongSupplier clock;

    public ScreenEditorService(ScreenService screens) {
        this(screens, System::currentTimeMillis);
    }

    public ScreenEditorService(ScreenService screens, LongSupplier clock) {
        this.screens = Objects.requireNonNull(screens, "screens");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public Result apply(EditorRequest request, boolean authorized, DimensionKey actorDimension) {
        Objects.requireNonNull(request, "request");
        if (!authorized) {
            return Result.failure(request.operationId(), "forbidden", "You do not have permission to edit screens.");
        }
        if (request.action() != EditorRequest.Action.SYNC && actorDimension == null) {
            return Result.failure(request.operationId(), "invalid_dimension", "Your current dimension is unavailable.");
        }

        try {
            long now = clock.getAsLong();
            switch (request.action()) {
                case CREATE:
                    requireDefinitionDimension(request.definition(), actorDimension);
                    return Result.upsert(request.operationId(), screens.create(request.definition(), now));
                case UPDATE:
                    requireDefinitionDimension(request.definition(), actorDimension);
                    requireScreen(request.screenId(), actorDimension);
                    return Result.upsert(request.operationId(), screens.updateDefinition(request.definition(), now));
                case DELETE:
                    requireScreen(request.screenId(), actorDimension);
                    ScreenState deleted = screens.delete(request.screenId());
                    return Result.deleted(request.operationId(), deleted.definition().id(), screens.revision());
                case SOURCE:
                    requireScreen(request.screenId(), actorDimension);
                    try {
                        MediaSources.validate(request.media());
                    } catch (IllegalArgumentException error) {
                        throw new Rejected("invalid_source", "The media source is not an allowed HTTPS source.");
                    }
                    return Result.upsert(request.operationId(),
                            screens.setMedia(request.screenId(), request.media(), now));
                case PLAY:
                    requireScreen(request.screenId(), actorDimension);
                    return Result.playback(request.operationId(),
                            screens.setPlayback(request.screenId(), PlaybackStatus.PLAYING, now));
                case PAUSE:
                    requireScreen(request.screenId(), actorDimension);
                    return Result.playback(request.operationId(),
                            screens.setPlayback(request.screenId(), PlaybackStatus.PAUSED, now));
                case STOP:
                    requireScreen(request.screenId(), actorDimension);
                    return Result.playback(request.operationId(),
                            screens.setPlayback(request.screenId(), PlaybackStatus.STOPPED, 0L, now));
                case SEEK:
                    requireScreen(request.screenId(), actorDimension);
                    return Result.playback(request.operationId(),
                            screens.seekPlayback(request.screenId(), seekValue(request.value()), now));
                case RATE:
                    requireScreen(request.screenId(), actorDimension);
                    return Result.playback(request.operationId(),
                            screens.setRate(request.screenId(), request.value(), now));
                case LOOP:
                    requireScreen(request.screenId(), actorDimension);
                    return Result.playback(request.operationId(),
                            screens.setLoop(request.screenId(), request.value() == 1.0, now));
                case AREA_CREATE:
                    requireAreaDimension(request.area(), actorDimension);
                    return Result.scene(request.operationId(), screens.createArea(request.area()));
                case AREA_UPDATE:
                    requireAreaDimension(request.area(), actorDimension);
                    requireArea(request.area().id(), actorDimension);
                    return Result.scene(request.operationId(), screens.updateArea(request.area()));
                case AREA_DELETE:
                    ViewingArea area = requireArea(request.screenId(), actorDimension);
                    return Result.deletedArea(request.operationId(), area.id(), screens.deleteArea(area.id()));
                case SYNC:
                    return Result.success(request.operationId(), "sync", "Screen state is ready to synchronize.");
                default:
                    throw new Rejected("unsupported", "That screen operation is not supported.");
            }
        } catch (Rejected error) {
            return Result.failure(request.operationId(), error.code, error.getMessage());
        } catch (ScreenConflictException error) {
            return Result.failure(request.operationId(), "conflict", "A screen or viewing area with that name already exists.");
        } catch (IllegalStateException error) {
            return Result.failure(request.operationId(), "invalid_state", "The screen is not in a valid state for that operation.");
        } catch (IllegalArgumentException error) {
            return Result.failure(request.operationId(), "invalid_request", "The screen operation is invalid.");
        } catch (IOException error) {
            return Result.failure(request.operationId(), "persistence", "The screen change could not be saved.");
        } catch (RuntimeException error) {
            return Result.failure(request.operationId(), "failed", "The screen operation failed.");
        }
    }

    private void requireDefinitionDimension(ScreenDefinition definition, DimensionKey actorDimension) {
        if (definition == null || !actorDimension.equals(definition.dimension())) {
            throw new Rejected("dimension_mismatch", "The screen must be in your current dimension.");
        }
    }

    private ScreenState requireScreen(UUID screenId, DimensionKey actorDimension) {
        ScreenState screen = screens.find(screenId);
        if (screen == null) {
            throw new Rejected("not_found", "That screen no longer exists.");
        }
        if (!actorDimension.equals(screen.definition().dimension())) {
            throw new Rejected("dimension_mismatch", "The screen must be in your current dimension.");
        }
        return screen;
    }

    private ViewingArea requireArea(UUID areaId, DimensionKey actorDimension) {
        ViewingArea area = screens.findArea(areaId);
        if (area == null) {
            throw new Rejected("not_found", "That viewing area no longer exists.");
        }
        if (!actorDimension.equals(area.dimension())) {
            throw new Rejected("dimension_mismatch", "The viewing area must be in your current dimension.");
        }
        return area;
    }

    private void requireAreaDimension(ViewingArea area, DimensionKey actorDimension) {
        if (area == null || !actorDimension.equals(area.dimension())) {
            throw new Rejected("dimension_mismatch", "The viewing area must be in your current dimension.");
        }
    }

    private static long seekValue(double value) {
        if (!Double.isFinite(value) || value < 0 || value != Math.rint(value)
                || value > Long.MAX_VALUE) {
            throw new Rejected("invalid_value", "Seek position must be a whole number of milliseconds.");
        }
        return (long) value;
    }

    private static final class Rejected extends RuntimeException {
        private final String code;

        private Rejected(String code, String message) {
            super(message);
            this.code = code;
        }
    }

    public static final class Result {
        public enum Change { NONE, UPSERT, PLAYBACK, DELETE, SCENE }

        private final UUID operationId;
        private final boolean success;
        private final String code;
        private final String message;
        private final Change change;
        private final ScreenState screen;
        private final UUID deletedScreenId;
        private final ViewingArea area;
        private final UUID deletedAreaId;
        private final long revision;

        private Result(
                UUID operationId,
                boolean success,
                String code,
                String message,
                Change change,
                ScreenState screen,
                UUID deletedScreenId,
                ViewingArea area,
                UUID deletedAreaId,
                long revision) {
            this.operationId = operationId;
            this.success = success;
            this.code = code;
            this.message = message;
            this.change = change;
            this.screen = screen;
            this.deletedScreenId = deletedScreenId;
            this.area = area;
            this.deletedAreaId = deletedAreaId;
            this.revision = revision;
        }

        private static Result success(UUID operationId, String code, String message) {
            return new Result(operationId, true, code, message, Change.NONE, null, null, null, null, -1L);
        }

        private static Result failure(UUID operationId, String code, String message) {
            return new Result(operationId, false, code, message, Change.NONE, null, null, null, null, -1L);
        }

        private static Result upsert(UUID operationId, ScreenState screen) {
            return new Result(operationId, true, "ok", "Screen saved.", Change.UPSERT, screen, null, null, null, screen.revision());
        }

        private static Result playback(UUID operationId, ScreenState screen) {
            return new Result(operationId, true, "ok", "Playback state saved.", Change.PLAYBACK, screen, null, null, null, screen.revision());
        }

        private static Result deleted(UUID operationId, UUID screenId, long revision) {
            return new Result(operationId, true, "ok", "Screen deleted.", Change.DELETE, null, screenId, null, null, revision);
        }

        private static Result scene(UUID operationId, ViewingArea area) {
            return new Result(operationId, true, "ok", "Viewing area saved.", Change.SCENE, null, null, area, null, -1L);
        }

        private static Result deletedArea(UUID operationId, UUID areaId, ViewingArea ignored) {
            return new Result(operationId, true, "ok", "Viewing area deleted.", Change.SCENE, null, null, null, areaId, -1L);
        }

        public UUID operationId() {
            return operationId;
        }

        public boolean success() {
            return success;
        }

        public String code() {
            return code;
        }

        public String message() {
            return message;
        }

        public Change change() {
            return change;
        }

        public ScreenState screen() {
            return screen;
        }

        public UUID deletedScreenId() {
            return deletedScreenId;
        }

        public ViewingArea area() {
            return area;
        }

        public UUID deletedAreaId() {
            return deletedAreaId;
        }

        public long revision() {
            return revision;
        }
    }
}
