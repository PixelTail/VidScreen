package dev.vidscreen.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.vidscreen.domain.BlockPoint;
import dev.vidscreen.domain.DimensionKey;
import dev.vidscreen.domain.Facing;
import dev.vidscreen.domain.MediaDescriptor;
import dev.vidscreen.domain.PlaybackStatus;
import dev.vidscreen.domain.ScreenDefinition;
import dev.vidscreen.domain.ScreenFit;
import dev.vidscreen.domain.ScreenGeometry;
import dev.vidscreen.domain.ScreenState;
import dev.vidscreen.domain.ScreenStyle;
import dev.vidscreen.domain.ViewingArea;
import dev.vidscreen.protocol.message.EditorRequest;

class ScreenEditorServiceTest {
    private static final DimensionKey OVERWORLD = new DimensionKey("minecraft:overworld");
    private static final DimensionKey NETHER = new DimensionKey("minecraft:the_nether");

    @TempDir
    Path directory;

    @Test
    void rejectsUnauthorizedMutationBeforePersistence() throws Exception {
        ScreenService screens = service();
        ScreenEditorService editor = new ScreenEditorService(screens, () -> 1_000L);
        ScreenDefinition definition = definition(UUID.randomUUID(), "lobby", OVERWORLD);

        ScreenEditorService.Result result = editor.apply(
                EditorRequest.definition(EditorRequest.Action.CREATE, definition), false, OVERWORLD);

        assertFalse(result.success());
        assertEquals("forbidden", result.code());
        assertTrue(screens.snapshot().isEmpty());
    }

    @Test
    void rejectsNonFiniteProtocolValues() {
        assertThrows(IllegalArgumentException.class, () -> EditorRequest.operation(
                EditorRequest.Action.RATE, UUID.randomUUID(), Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> EditorRequest.operation(
                EditorRequest.Action.RATE, UUID.randomUUID(), Double.POSITIVE_INFINITY));
    }

    @Test
    void enforcesActorDimensionForCreateAndExistingScreens() throws Exception {
        ScreenService screens = service();
        ScreenEditorService editor = new ScreenEditorService(screens, () -> 1_000L);
        ScreenDefinition definition = definition(UUID.randomUUID(), "lobby", NETHER);

        ScreenEditorService.Result create = editor.apply(
                EditorRequest.definition(EditorRequest.Action.CREATE, definition), true, OVERWORLD);
        assertFalse(create.success());
        assertEquals("dimension_mismatch", create.code());

        ScreenState created = screens.create(definition(UUID.randomUUID(), "lobby", OVERWORLD), 1_000L);
        ScreenEditorService.Result source = editor.apply(
                EditorRequest.source(created.definition().id(),
                        new MediaDescriptor("direct", "https://media.example/video.mp4")),
                true,
                NETHER);
        assertFalse(source.success());
        assertEquals("dimension_mismatch", source.code());
    }

    @Test
    void validatesSourceRulesAndKeepsUrlOutOfFailureMessages() throws Exception {
        ScreenService screens = service();
        ScreenEditorService editor = new ScreenEditorService(screens, () -> 1_000L);
        ScreenState created = screens.create(definition(UUID.randomUUID(), "lobby", OVERWORLD), 1_000L);

        ScreenEditorService.Result result = editor.apply(
                EditorRequest.source(created.definition().id(),
                        new MediaDescriptor("direct", "https://media.example/video.webm")),
                true,
                OVERWORLD);

        assertFalse(result.success());
        assertEquals("invalid_source", result.code());
        assertFalse(result.message().contains("media.example"));
    }

    @Test
    void commitsEditorChangesAndPreservesThemAfterReload() throws Exception {
        Path file = directory.resolve("screens.bin");
        ScreenService screens = new ScreenService(new FileScreenRepository(file));
        screens.load();
        ScreenEditorService editor = new ScreenEditorService(screens, () -> 2_000L);
        ScreenDefinition definition = definition(UUID.randomUUID(), "lobby", OVERWORLD);

        ScreenEditorService.Result created = editor.apply(
                EditorRequest.definition(EditorRequest.Action.CREATE, definition), true, OVERWORLD);
        assertTrue(created.success());
        ScreenState screen = created.screen();
        editor.apply(EditorRequest.source(screen.definition().id(),
                new MediaDescriptor("direct", "https://media.example/video.mp4")), true, OVERWORLD);
        ScreenEditorService.Result playing = editor.apply(
                EditorRequest.operation(EditorRequest.Action.PLAY, screen.definition().id(), 0), true, OVERWORLD);
        assertTrue(playing.success());
        assertEquals(PlaybackStatus.PLAYING, playing.screen().playback().status());

        ScreenService reloaded = new ScreenService(new FileScreenRepository(file));
        reloaded.load();
        assertEquals(PlaybackStatus.PLAYING, reloaded.find(screen.definition().id()).playback().status());
    }

    @Test
    void appliesViewingAreaOperationsAndRejectsCrossDimensionRequests() throws Exception {
        ScreenService screens = service();
        ScreenEditorService editor = new ScreenEditorService(screens, () -> 2_000L);
        ViewingArea area = new ViewingArea(
                UUID.randomUUID(), "lobby", OVERWORLD,
                new BlockPoint(0, 60, 0), new BlockPoint(10, 80, 10));

        ScreenEditorService.Result denied = editor.apply(
                EditorRequest.area(EditorRequest.Action.AREA_CREATE, area), true, NETHER);
        assertFalse(denied.success());
        assertEquals("dimension_mismatch", denied.code());

        ScreenEditorService.Result created = editor.apply(
                EditorRequest.area(EditorRequest.Action.AREA_CREATE, area), true, OVERWORLD);
        assertTrue(created.success());
        assertEquals(ScreenEditorService.Result.Change.SCENE, created.change());

        ScreenDefinition bound = new ScreenDefinition(
                UUID.randomUUID(), "bound", OVERWORLD,
                ScreenGeometry.between(new BlockPoint(1, 64, 8), new BlockPoint(4, 66, 8), Facing.NORTH),
                ScreenFit.CONTAIN, 96, ScreenStyle.FLAT, area.id());
        assertTrue(editor.apply(EditorRequest.definition(EditorRequest.Action.CREATE, bound), true, OVERWORLD).success());
        ScreenEditorService.Result deleted = editor.apply(
                new EditorRequest(UUID.randomUUID(), EditorRequest.Action.AREA_DELETE, area.id(),
                        null, null, 0), true, OVERWORLD);
        assertFalse(deleted.success());
        assertEquals("invalid_state", deleted.code());
    }

    private ScreenService service() throws Exception {
        ScreenService service = new ScreenService(new FileScreenRepository(directory.resolve(UUID.randomUUID() + ".bin")));
        service.load();
        return service;
    }

    private static ScreenDefinition definition(UUID id, String name, DimensionKey dimension) {
        return new ScreenDefinition(
                id,
                name,
                dimension,
                ScreenGeometry.between(new BlockPoint(1, 64, 8), new BlockPoint(4, 66, 8), Facing.NORTH),
                ScreenFit.CONTAIN,
                96);
    }
}
