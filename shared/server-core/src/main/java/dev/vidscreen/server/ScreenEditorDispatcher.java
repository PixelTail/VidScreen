package dev.vidscreen.server;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import dev.vidscreen.domain.DimensionKey;
import dev.vidscreen.protocol.message.EditorRequest;

/**
 * Bounded asynchronous boundary for editor persistence.
 *
 * <p>The Minecraft server thread only validates the connection/session and
 * enqueues work.  The single worker owns persistence calls, while the
 * platform adapter decides how and whether the result is delivered back on
 * the live server thread.</p>
 */
public final class ScreenEditorDispatcher implements AutoCloseable {
    private static final int QUEUE_CAPACITY = 32;

    private final ScreenEditorService editor;
    private final ThreadPoolExecutor executor;
    private final Set<UUID> inFlight = ConcurrentHashMap.newKeySet();
    private volatile boolean closed;

    public ScreenEditorDispatcher(ScreenEditorService editor) {
        this.editor = Objects.requireNonNull(editor, "editor");
        this.executor = new ThreadPoolExecutor(
                1,
                1,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<Runnable>(QUEUE_CAPACITY),
                new EditorThreadFactory(),
                new ThreadPoolExecutor.AbortPolicy());
    }

    public SubmitResult submit(
            UUID playerId,
            EditorRequest request,
            boolean authorized,
            DimensionKey actorDimension,
            Consumer<ScreenEditorService.Result> completion) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(completion, "completion");
        if (closed) {
            return SubmitResult.CLOSED;
        }
        if (!inFlight.add(playerId)) {
            return SubmitResult.BUSY;
        }
        try {
            executor.execute(() -> {
                try {
                    ScreenEditorService.Result result = editor.apply(request, authorized, actorDimension);
                    completion.accept(result);
                } finally {
                    inFlight.remove(playerId);
                }
            });
            return SubmitResult.ACCEPTED;
        } catch (RejectedExecutionException error) {
            inFlight.remove(playerId);
            return closed ? SubmitResult.CLOSED : SubmitResult.QUEUE_FULL;
        }
    }

    @Override
    public void close() {
        shutdown();
    }

    /**
     * Stops accepting work, drops queued requests, then gives the active
     * persistence operation two seconds to drain.  A final interrupt and
     * 500ms wait bound shutdown when the repository does not respond.
     */
    public ShutdownResult shutdown() {
        closed = true;
        int queued = executor.getQueue().size();
        executor.getQueue().clear();
        executor.shutdown();
        boolean terminated = awaitTermination(2, TimeUnit.SECONDS);
        boolean forced = false;
        if (!terminated) {
            forced = true;
            executor.shutdownNow();
            terminated = awaitTermination(500, TimeUnit.MILLISECONDS);
        }
        inFlight.clear();
        return new ShutdownResult(terminated, forced, queued);
    }

    private boolean awaitTermination(long timeout, TimeUnit unit) {
        try {
            return executor.awaitTermination(timeout, unit);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
            return executor.isTerminated();
        }
    }

    public enum SubmitResult {
        ACCEPTED,
        BUSY,
        QUEUE_FULL,
        CLOSED
    }

    public static final class ShutdownResult {
        private final boolean terminated;
        private final boolean forced;
        private final int queuedTasksDiscarded;

        private ShutdownResult(boolean terminated, boolean forced, int queuedTasksDiscarded) {
            this.terminated = terminated;
            this.forced = forced;
            this.queuedTasksDiscarded = queuedTasksDiscarded;
        }

        public boolean terminated() {
            return terminated;
        }

        public boolean forced() {
            return forced;
        }

        public int queuedTasksDiscarded() {
            return queuedTasksDiscarded;
        }
    }

    private static final class EditorThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "vidscreen-editor");
            thread.setDaemon(true);
            return thread;
        }
    }
}
