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

    private static ScreenDefinition definition(String name) {
        return new ScreenDefinition(
                UUID.randomUUID(),
                name,
                new DimensionKey("minecraft:overworld"),
                ScreenGeometry.between(new BlockPoint(1, 64, 8), new BlockPoint(4, 66, 8), Facing.NORTH),
                ScreenFit.CONTAIN,
                96);
    }
}
