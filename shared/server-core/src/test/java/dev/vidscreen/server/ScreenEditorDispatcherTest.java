package dev.vidscreen.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import dev.vidscreen.domain.BlockPoint;
import dev.vidscreen.domain.DimensionKey;
import dev.vidscreen.domain.Facing;
import dev.vidscreen.domain.ScreenDefinition;
import dev.vidscreen.domain.ScreenFit;
import dev.vidscreen.domain.ScreenGeometry;
import dev.vidscreen.domain.ScreenState;
import dev.vidscreen.protocol.message.EditorRequest;

class ScreenEditorDispatcherTest {
    private static final DimensionKey OVERWORLD = new DimensionKey("minecraft:overworld");

    @Test
    void permitsOnlyOneInFlightRequestPerPlayer() throws Exception {
        BlockingRepository repository = new BlockingRepository();
        ScreenEditorDispatcher dispatcher = new ScreenEditorDispatcher(editor(repository));
        UUID playerId = UUID.randomUUID();
        CountDownLatch completed = new CountDownLatch(1);

        assertEquals(ScreenEditorDispatcher.SubmitResult.ACCEPTED, dispatcher.submit(
                playerId,
                createRequest(),
                true,
                OVERWORLD,
                result -> completed.countDown()));
        assertTrue(repository.saveStarted.await(5, TimeUnit.SECONDS));

        assertEquals(ScreenEditorDispatcher.SubmitResult.BUSY, dispatcher.submit(
                playerId,
                createRequest(),
                true,
                OVERWORLD,
                result -> { }));

        repository.releaseSave.countDown();
        assertTrue(completed.await(5, TimeUnit.SECONDS));
        assertTrue(dispatcher.shutdown().terminated());
    }

    @Test
    void rejectsNewWorkAfterShutdown() throws Exception {
        ScreenEditorDispatcher dispatcher = new ScreenEditorDispatcher(editor(new EmptyRepository()));

        ScreenEditorDispatcher.ShutdownResult shutdown = dispatcher.shutdown();

        assertTrue(shutdown.terminated());
        assertEquals(ScreenEditorDispatcher.SubmitResult.CLOSED, dispatcher.submit(
                UUID.randomUUID(),
                createRequest(),
                true,
                OVERWORLD,
                result -> { }));
    }

    @Test
    void interruptsAnUnresponsiveWorkerWithinTheBoundedShutdownWindow() throws Exception {
        BlockingRepository repository = new BlockingRepository();
        ScreenEditorDispatcher dispatcher = new ScreenEditorDispatcher(editor(repository));
        assertEquals(ScreenEditorDispatcher.SubmitResult.ACCEPTED, dispatcher.submit(
                UUID.randomUUID(),
                createRequest(),
                true,
                OVERWORLD,
                result -> { }));
        assertTrue(repository.saveStarted.await(5, TimeUnit.SECONDS));

        ScreenEditorDispatcher.ShutdownResult shutdown = dispatcher.shutdown();

        assertTrue(shutdown.forced());
        assertTrue(shutdown.terminated());
        assertTrue(repository.interrupted);
    }

    private static ScreenService service(ScreenRepository repository) throws IOException {
        ScreenService service = new ScreenService(repository);
        service.load();
        return service;
    }

    private static ScreenEditorService editor(ScreenRepository repository) throws IOException {
        return new ScreenEditorService(service(repository));
    }

    private static EditorRequest createRequest() {
        return EditorRequest.definition(EditorRequest.Action.CREATE, new ScreenDefinition(
                UUID.randomUUID(),
                "screen-" + UUID.randomUUID().toString().substring(0, 8),
                OVERWORLD,
                ScreenGeometry.between(
                        new BlockPoint(1, 64, 8),
                        new BlockPoint(4, 66, 8),
                        Facing.NORTH),
                ScreenFit.CONTAIN,
                96));
    }

    private static class EmptyRepository implements ScreenRepository {
        @Override
        public Collection<ScreenState> load() {
            return Collections.emptyList();
        }

        @Override
        public void save(long revision, Collection<ScreenState> screens) throws IOException {
        }
    }

    private static final class BlockingRepository extends EmptyRepository {
        private final CountDownLatch saveStarted = new CountDownLatch(1);
        private final CountDownLatch releaseSave = new CountDownLatch(1);
        private volatile boolean interrupted;

        @Override
        public void save(long revision, Collection<ScreenState> screens) throws IOException {
            saveStarted.countDown();
            try {
                releaseSave.await();
            } catch (InterruptedException error) {
                interrupted = true;
                Thread.currentThread().interrupt();
                throw new IOException("save interrupted", error);
            }
        }
    }
}
