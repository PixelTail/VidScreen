package dev.vidscreen.media.ffmpeg.nativeapi;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;

import dev.vidscreen.media.MediaPlayer;
import dev.vidscreen.media.ResolvedMedia;
import dev.vidscreen.media.VideoFrame;
import dev.vidscreen.media.VideoFrameSink;

import static org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_RGBA;

public final class NativeFfmpegVideoPlayer implements MediaPlayer {
    private static final long IO_TIMEOUT_MICROS = 15_000_000L;
    private static final long MAX_WAIT_SLICE_NANOS = TimeUnit.MILLISECONDS.toNanos(10);

    private final int outputWidth;
    private final int outputHeight;
    private final ExecutorService executor;
    private final Object lock = new Object();

    private ResolvedMedia media;
    private VideoFrameSink frameSink;
    private FFmpegFrameGrabber grabber;
    private CompletableFuture<Void> opening;
    private long generation;
    private long positionMicros;
    private long pendingSeekMicros = -1;
    private long timingRevision;
    private double rate = 1.0;
    private boolean playing;
    private boolean frameRequested;
    private boolean ready;
    private boolean closed;

    public NativeFfmpegVideoPlayer(int outputWidth, int outputHeight) {
        if (outputWidth < 16 || outputHeight < 16 || outputWidth > 8_192 || outputHeight > 8_192) {
            throw new IllegalArgumentException("Output dimensions must be between 16 and 8192");
        }
        long frameBytes = (long) outputWidth * outputHeight * 4;
        if (frameBytes > 256L * 1024 * 1024) {
            throw new IllegalArgumentException("Output frame exceeds 256 MiB");
        }
        this.outputWidth = outputWidth;
        this.outputHeight = outputHeight;
        this.executor = Executors.newSingleThreadExecutor(new DecoderThreadFactory());
    }

    @Override
    public CompletionStage<Void> open(ResolvedMedia resolved, VideoFrameSink sink) {
        Objects.requireNonNull(resolved, "resolved");
        Objects.requireNonNull(sink, "sink");
        CompletableFuture<Void> future = new CompletableFuture<Void>();
        long expectedGeneration;
        synchronized (lock) {
            requireOpen();
            if (opening != null || media != null) {
                throw new IllegalStateException("A media source is already open");
            }
            media = resolved;
            frameSink = sink;
            opening = future;
            expectedGeneration = ++generation;
        }
        try {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    decode(expectedGeneration, resolved, sink, future);
                }
            });
        } catch (RejectedExecutionException error) {
            synchronized (lock) {
                opening = null;
                media = null;
                frameSink = null;
            }
            future.completeExceptionally(new NativeFfmpegException("The native decoder is closed"));
        }
        return future;
    }

    @Override
    public void play() {
        synchronized (lock) {
            requireReady();
            if (!playing) {
                playing = true;
                timingRevision++;
                lock.notifyAll();
            }
        }
    }

    @Override
    public void pause() {
        synchronized (lock) {
            requireReady();
            if (playing) {
                playing = false;
                timingRevision++;
                lock.notifyAll();
            }
        }
    }

    @Override
    public void seek(Duration position) {
        Objects.requireNonNull(position, "position");
        if (position.isNegative()) {
            throw new IllegalArgumentException("position must be non-negative");
        }
        long requestedMicros = Math.multiplyExact(position.toMillis(), 1_000L);
        synchronized (lock) {
            requireReady();
            positionMicros = requestedMicros;
            pendingSeekMicros = requestedMicros;
            timingRevision++;
            lock.notifyAll();
        }
    }

    @Override
    public void requestFrame() {
        synchronized (lock) {
            requireReady();
            if (!playing) {
                frameRequested = true;
                lock.notifyAll();
            }
        }
    }

    @Override
    public Duration position() {
        synchronized (lock) {
            return Duration.ofMillis(positionMicros / 1_000L);
        }
    }

    @Override
    public boolean supportsDynamicRateAdjustment() {
        return true;
    }

    @Override
    public void setRate(double newRate) {
        if (!Double.isFinite(newRate) || newRate < 0.25 || newRate > 4.0) {
            throw new IllegalArgumentException("rate must be finite and between 0.25 and 4.0");
        }
        synchronized (lock) {
            requireReady();
            if (Double.compare(rate, newRate) != 0) {
                rate = newRate;
                timingRevision++;
                lock.notifyAll();
            }
        }
    }

    private void decode(
            long expectedGeneration,
            ResolvedMedia resolved,
            VideoFrameSink sink,
            CompletableFuture<Void> openFuture) {
        FFmpegFrameGrabber decoder = null;
        boolean started = false;
        try {
            NativeFfmpegRuntime.requireAvailable();
            decoder = createGrabber(resolved);
            decoder.start();
            started = true;

            int sourceWidth = decoder.getImageWidth();
            int sourceHeight = decoder.getImageHeight();
            RgbaFrameCopier.Dimensions dimensions = RgbaFrameCopier.fit(
                    sourceWidth, sourceHeight, outputWidth, outputHeight);
            decoder.setImageWidth(dimensions.width());
            decoder.setImageHeight(dimensions.height());

            synchronized (lock) {
                if (!isCurrent(expectedGeneration)) {
                    openFuture.completeExceptionally(new NativeFfmpegException("Media opening was cancelled"));
                    return;
                }
                grabber = decoder;
                opening = null;
                ready = true;
            }
            openFuture.complete(null);
            decodeFrames(expectedGeneration, decoder, sink);
        } catch (java.lang.Exception | LinkageError error) {
            NativeFfmpegException sanitized = new NativeFfmpegException(
                    started ? "Native media decoding failed" : "Could not open the native media decoder");
            synchronized (lock) {
                if (isCurrent(expectedGeneration)) {
                    ready = false;
                    playing = false;
                    grabber = null;
                    if (opening == openFuture) {
                        opening = null;
                    }
                }
            }
            if (!openFuture.isDone()) {
                openFuture.completeExceptionally(sanitized);
            }
        } finally {
            closeGrabber(decoder);
            synchronized (lock) {
                if (grabber == decoder) {
                    grabber = null;
                    ready = false;
                    playing = false;
                }
            }
        }
    }

    private void decodeFrames(long expectedGeneration, FFmpegFrameGrabber decoder, VideoFrameSink sink)
            throws java.lang.Exception {
        PacingClock pacing = new PacingClock();
        while (true) {
            DecodeAction action = awaitAction(expectedGeneration);
            if (action == null) {
                return;
            }
            if (action.seekMicros() >= 0) {
                decoder.setTimestamp(action.seekMicros(), true);
                pacing.reset();
            }
            if (!action.decodeFrame()) {
                continue;
            }

            Frame decoded = decoder.grabImage();
            if (decoded == null) {
                synchronized (lock) {
                    if (isCurrent(expectedGeneration)) {
                        playing = false;
                        timingRevision++;
                    }
                }
                pacing.reset();
                continue;
            }
            long presentationTimeMicros = decoded.timestamp >= 0
                    ? decoded.timestamp
                    : decoder.getTimestamp();
            if (!awaitPresentation(expectedGeneration, action, presentationTimeMicros, pacing)) {
                continue;
            }

            VideoFrame frame = RgbaFrameCopier.copy(
                    decoded, outputWidth, outputHeight, presentationTimeMicros);
            boolean submit;
            synchronized (lock) {
                submit = isCurrent(expectedGeneration)
                        && timingRevision == action.timingRevision()
                        && (playing || action.singleFrame());
                if (submit) {
                    positionMicros = Math.max(0L, presentationTimeMicros);
                }
            }
            if (submit) {
                sink.submit(frame);
            } else {
                frame.close();
            }
        }
    }

    private DecodeAction awaitAction(long expectedGeneration) throws InterruptedException {
        synchronized (lock) {
            while (isCurrent(expectedGeneration)
                    && !playing
                    && !frameRequested
                    && pendingSeekMicros < 0) {
                lock.wait();
            }
            if (!isCurrent(expectedGeneration)) {
                return null;
            }
            long seekMicros = pendingSeekMicros;
            pendingSeekMicros = -1;
            boolean singleFrame = frameRequested && !playing;
            frameRequested = false;
            return new DecodeAction(
                    seekMicros,
                    playing || singleFrame,
                    singleFrame,
                    timingRevision,
                    rate);
        }
    }

    private boolean awaitPresentation(
            long expectedGeneration,
            DecodeAction action,
            long presentationTimeMicros,
            PacingClock pacing) throws InterruptedException {
        if (action.singleFrame()) {
            pacing.reset();
            return true;
        }
        if (!pacing.initialized() || pacing.timingRevision() != action.timingRevision()
                || presentationTimeMicros < pacing.mediaMicros()) {
            pacing.start(action.timingRevision(), presentationTimeMicros, System.nanoTime());
            return true;
        }
        long mediaDeltaMicros = presentationTimeMicros - pacing.mediaMicros();
        long scaledDeltaNanos = Math.max(0L, Math.round(mediaDeltaMicros * 1_000.0 / action.rate()));
        long targetNanos = pacing.wallNanos() + scaledDeltaNanos;
        synchronized (lock) {
            while (isCurrent(expectedGeneration)
                    && playing
                    && timingRevision == action.timingRevision()) {
                long remaining = targetNanos - System.nanoTime();
                if (remaining <= 0) {
                    return true;
                }
                TimeUnit.NANOSECONDS.timedWait(lock, Math.min(remaining, MAX_WAIT_SLICE_NANOS));
            }
            return false;
        }
    }

    private static FFmpegFrameGrabber createGrabber(ResolvedMedia resolved) {
        FFmpegFrameGrabber decoder = new FFmpegFrameGrabber(resolved.streamUri().toASCIIString());
        decoder.setPixelFormat(AV_PIX_FMT_RGBA);
        decoder.setOption("protocol_whitelist", "crypto,https,tls,tcp");
        decoder.setOption("rw_timeout", Long.toString(IO_TIMEOUT_MICROS));
        decoder.setOption("timeout", Long.toString(IO_TIMEOUT_MICROS));
        decoder.setOption("probesize", "5000000");
        decoder.setOption("analyzeduration", "5000000");
        return decoder;
    }

    private boolean isCurrent(long expectedGeneration) {
        return !closed && generation == expectedGeneration;
    }

    private void requireReady() {
        requireOpen();
        if (!ready || media == null || frameSink == null || grabber == null) {
            throw new IllegalStateException("No media is open");
        }
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("Media player is closed");
        }
    }

    @Override
    public void close() {
        VideoFrameSink sink;
        CompletableFuture<Void> openFuture;
        synchronized (lock) {
            if (closed) {
                return;
            }
            closed = true;
            generation++;
            playing = false;
            ready = false;
            frameRequested = false;
            pendingSeekMicros = -1;
            sink = frameSink;
            frameSink = null;
            media = null;
            openFuture = opening;
            opening = null;
            lock.notifyAll();
        }
        if (openFuture != null) {
            openFuture.completeExceptionally(new NativeFfmpegException("Media opening was cancelled"));
        }
        if (sink != null) {
            sink.close();
        }
        executor.shutdownNow();
    }

    private static void closeGrabber(FFmpegFrameGrabber decoder) {
        if (decoder == null) {
            return;
        }
        try {
            decoder.close();
        } catch (java.lang.Exception ignored) {
        }
    }

    private static final class DecodeAction {
        private final long seekMicros;
        private final boolean decodeFrame;
        private final boolean singleFrame;
        private final long timingRevision;
        private final double rate;

        private DecodeAction(
                long seekMicros,
                boolean decodeFrame,
                boolean singleFrame,
                long timingRevision,
                double rate) {
            this.seekMicros = seekMicros;
            this.decodeFrame = decodeFrame;
            this.singleFrame = singleFrame;
            this.timingRevision = timingRevision;
            this.rate = rate;
        }

        long seekMicros() {
            return seekMicros;
        }

        boolean decodeFrame() {
            return decodeFrame;
        }

        boolean singleFrame() {
            return singleFrame;
        }

        long timingRevision() {
            return timingRevision;
        }

        double rate() {
            return rate;
        }
    }

    private static final class PacingClock {
        private boolean initialized;
        private long timingRevision;
        private long mediaMicros;
        private long wallNanos;

        boolean initialized() {
            return initialized;
        }

        long timingRevision() {
            return timingRevision;
        }

        long mediaMicros() {
            return mediaMicros;
        }

        long wallNanos() {
            return wallNanos;
        }

        void start(long revision, long mediaTimeMicros, long wallTimeNanos) {
            initialized = true;
            timingRevision = revision;
            mediaMicros = mediaTimeMicros;
            wallNanos = wallTimeNanos;
        }

        void reset() {
            initialized = false;
        }
    }

    private static final class DecoderThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "vidscreen-native-ffmpeg");
            thread.setDaemon(true);
            return thread;
        }
    }
}
