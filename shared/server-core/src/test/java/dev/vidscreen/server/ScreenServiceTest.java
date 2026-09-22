package dev.vidscreen.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
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
import dev.vidscreen.domain.ViewingArea;

class ScreenServiceTest {
    @TempDir
    Path directory;

    @Test
    void persistsAndReloadsScreenLifecycle() throws Exception {
        Path file = directory.resolve("screens.bin");
        ScreenService service = new ScreenService(new FileScreenRepository(file));
        service.load();
        ScreenState created = service.create(definition("lobby"), 1_000);
        service.setMedia(created.definition().id(),
                new MediaDescriptor("direct", "https://media.example/video.mp4"), 1_100);
        service.setPlayback(created.definition().id(), PlaybackStatus.PLAYING, 500, 1_200);

        ScreenService reloaded = new ScreenService(new FileScreenRepository(file));
        reloaded.load();
        ScreenState screen = reloaded.findByName("LOBBY");

        assertNotNull(screen);
        assertEquals(PlaybackStatus.PLAYING, screen.playback().status());
        assertEquals("direct", screen.media().resolverId());
    }

    @Test
    void rejectsDuplicateNamesAndMissingMediaPlayback() throws Exception {
        ScreenService service = new ScreenService(new FileScreenRepository(directory.resolve("screens.bin")));
        service.load();
        ScreenState created = service.create(definition("lobby"), 1_000);

        assertThrows(ScreenConflictException.class, () -> service.create(definition("LOBBY"), 1_000));
        assertThrows(IllegalStateException.class,
                () -> service.setPlayback(created.definition().id(), PlaybackStatus.PLAYING, 0, 1_000));
    }

    @Test
    void deleteIsPersisted() throws Exception {
        Path file = directory.resolve("screens.bin");
        ScreenService service = new ScreenService(new FileScreenRepository(file));
        service.load();
        ScreenState created = service.create(definition("lobby"), 1_000);
        service.delete(created.definition().id());

        ScreenService reloaded = new ScreenService(new FileScreenRepository(file));
        reloaded.load();
        assertNull(reloaded.find(created.definition().id()));
        assertEquals(Collections.emptyList(), reloaded.snapshot());
    }

    @Test
    void updateDefinitionPreservesMediaAndPlayback() throws Exception {
        Path file = directory.resolve("screens.bin");
        ScreenService service = new ScreenService(new FileScreenRepository(file));
        service.load();
        ScreenState created = service.create(definition("lobby"), 1_000);
        MediaDescriptor media = new MediaDescriptor("direct", "https://media.example/video.mp4");
        service.setMedia(created.definition().id(), media, 1_100);
        service.setPlayback(created.definition().id(), PlaybackStatus.PAUSED, 500, 1_200);

        ScreenDefinition updatedDefinition = new ScreenDefinition(
                created.definition().id(),
                "updated",
                created.definition().dimension(),
                ScreenGeometry.between(new BlockPoint(10, 70, 8), new BlockPoint(13, 72, 8), Facing.NORTH),
                ScreenFit.CONTAIN,
                128);
        ScreenState updated = service.updateDefinition(updatedDefinition, 2_000);

        assertEquals("updated", updated.definition().name());
        assertEquals(media, updated.media());
        assertEquals(PlaybackStatus.PAUSED, updated.playback().status());
        assertEquals(500, updated.playback().mediaPositionMillis());
        assertEquals(updated.revision(), updated.playback().revision());
    }

    @Test
    void persistsViewingAreasAndProtectsBoundAreas() throws Exception {
        Path file = directory.resolve("scene.bin");
        ScreenService service = new ScreenService(new FileScreenRepository(file));
        service.load();
        ScreenState existing = service.create(definition("existing"), 900);
        ViewingArea area = area(UUID.randomUUID(), "lobby", new DimensionKey("minecraft:overworld"));
        service.createArea(area);
        assertNotNull(service.find(existing.definition().id()));

        ScreenDefinition bound = new ScreenDefinition(
                UUID.randomUUID(),
                "bound",
                area.dimension(),
                ScreenGeometry.between(new BlockPoint(1, 64, 8), new BlockPoint(4, 66, 8), Facing.NORTH),
                ScreenFit.CONTAIN,
                96,
                dev.vidscreen.domain.ScreenStyle.FLAT,
                area.id());
        service.create(bound, 1_000);
        assertEquals(area, service.findArea(area.id()));
        service.setMedia(bound.id(), new MediaDescriptor("direct", "https://media.example/bound.mp4"), 1_100);
        assertNotNull(service.find(bound.id()));
        assertEquals(area, service.findArea(area.id()));
        assertThrows(IllegalStateException.class, () -> service.deleteArea(area.id()));

        ScreenService reloaded = new ScreenService(new FileScreenRepository(file));
        reloaded.load();
        assertEquals(area, reloaded.findArea(area.id()));
        assertNotNull(reloaded.find(existing.definition().id()));
        assertNotNull(reloaded.find(bound.id()));
    }

    private static ScreenDefinition definition(String name) {
        return new ScreenDefinition(
                UUID.randomUUID(),
                name,
                new DimensionKey("minecraft:overworld"),
                ScreenGeometry.between(new BlockPoint(1, 64, 8), new BlockPoint(4, 66, 8), Facing.NORTH),
                ScreenFit.CONTAIN,
                96);
    }

    private static ViewingArea area(UUID id, String name, DimensionKey dimension) {
        return new ViewingArea(id, name, dimension,
                new BlockPoint(0, 60, 0), new BlockPoint(10, 80, 10));
    }
}
