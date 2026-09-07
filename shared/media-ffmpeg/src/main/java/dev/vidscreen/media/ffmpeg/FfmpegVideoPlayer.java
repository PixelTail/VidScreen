package dev.vidscreen.media.ffmpeg;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import dev.vidscreen.media.MediaPlayer;
import dev.vidscreen.media.PixelFormat;
import dev.vidscreen.media.ResolvedMedia;
import dev.vidscreen.media.VideoFrame;
import dev.vidscreen.media.VideoFrameSink;

public final class FfmpegVideoPlayer implements MediaPlayer {
    private final Path ffmpeg;
    private final Ffprobe ffprobe;
    private final int outputWidth;
    private final int outputHeight;
    private final ExecutorService executor;

    private final Object lock = new Object();
    private ResolvedMedia media;
    private VideoFrameSink frameSink;
    private Ffprobe.ProbeResult probe;
    private Process process;
    private FutureRun run;
    private long positionMicros;
    private double rate = 1.0;
    private boolean playing;
    private boolean closed;

    public FfmpegVideoPlayer(Path ffmpeg, Path ffprobe, int outputWidth, int outputHeight) {
        this.ffmpeg = Objects.requireNonNull(ffmpeg, "ffmpeg");
        this.ffprobe = new Ffprobe(Objects.requireNonNull(ffprobe, "ffprobe"));
        if (outputWidth < 16 || outputHeight < 16 || outputWidth > 8_192 || outputHeight > 8_192) {
            throw new IllegalArgumentException("Output dimensions must be between 16 and 8192");
        }
        long frameBytes = (long) outputWidth * outputHeight * 4;
        if (frameBytes > 256L * 1024 * 1024) {
            throw new IllegalArgumentException("Output frame exceeds 256 MiB");
        }
        this.outputWidth = outputWidth;
        this.outputHeight = outputHeight;
        this.executor = Executors.newCachedThreadPool(new NamedThreadFactory());
    }

    @Override
    public CompletionStage<Void> open(ResolvedMedia resolved, VideoFrameSink sink) {
        Objects.requireNonNull(resolved, "resolved");
        Objects.requireNonNull(sink, "sink");
        return CompletableFuture.runAsync(() -> {
            try {
                Ffprobe.ProbeResult inspected = ffprobe.inspect(resolved.streamUri().toASCIIString());
                synchronized (lock) {
                    requireOpen();
                    stopProcessLocked();
                    media = resolved;
                    frameSink = sink;
                    probe = inspected;
                    positionMicros = 0;
                    playing = false;
                }
            } catch (IOException error) {
                throw new FfmpegException("Could not probe media", error);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new FfmpegException("Media probe was interrupted", error);
            }
        }, executor);
    }

    @Override
    public void play() {
        synchronized (lock) {
            requireReady();
            if (playing) {
                return;
            }
            playing = true;
            startProcessLocked(false);
        }
    }

    @Override
    public void pause() {
        synchronized (lock) {
            requireReady();
            if (!playing) {
                return;
            }
            playing = false;
            stopProcessLocked();
        }
    }

    @Override
    public void seek(Duration position) {
        Objects.requireNonNull(position, "position");
        if (position.isNegative()) {
            throw new IllegalArgumentException("position must be non-negative");
        }
        synchronized (lock) {
            requireReady();
            positionMicros = Math.multiplyExact(position.toMillis(), 1_000);
            boolean restart = playing;
            stopProcessLocked();
            if (restart) {
                startProcessLocked(false);
            }
        }
    }

    @Override
    public void requestFrame() {
        synchronized (lock) {
            requireReady();
            if (!playing) {
                stopProcessLocked();
                startProcessLocked(true);
            }
        }
    }

    @Override
    public void setRate(double newRate) {
        if (!Double.isFinite(newRate) || newRate < 0.25 || newRate > 4.0) {
            throw new IllegalArgumentException("rate must be finite and between 0.25 and 4.0");
        }
        synchronized (lock) {
            requireReady();
            if (Double.compare(rate, newRate) == 0) {
                return;
            }
            rate = newRate;
            boolean restart = playing;
            stopProcessLocked();
            if (restart) {
                startProcessLocked(false);
            }
        }
    }

    @Override
    public Duration position() {
        synchronized (lock) {
            return Duration.ofMillis(positionMicros / 1_000);
        }
    }

    public long positionMicros() {
        synchronized (lock) {
            return positionMicros;
        }
    }

    private void startProcessLocked(boolean singleFrame) {
        long generationPosition = positionMicros;
        double generationRate = rate;
        Ffprobe.ProbeResult generationProbe = probe;
        VideoFrameSink generationSink = frameSink;
        List<String> command = command(generationPosition, generationRate, generationProbe.frameRate(), singleFrame);
        try {
            Process started = new ProcessBuilder(command).start();
            process = started;
            FutureRun next = new FutureRun(started);
            run = next;
            executor.execute(() -> drainErrors(next));
            executor.execute(() -> decode(next, generationPosition, generationRate, generationProbe.frameRate(), generationSink));
        } catch (IOException error) {
            playing = false;
            throw new FfmpegException("Could not start ffmpeg", error);
        }
    }

    private List<String> command(
            long startMicros,
            double playbackRate,
            double sourceFrameRate,
            boolean singleFrame) {
        double targetFrameRate = Math.min(60.0, sourceFrameRate * playbackRate);
        List<String> command = new ArrayList<String>();
        command.add(ffmpeg.toString());
        command.add("-nostdin");
        command.add("-hide_banner");
        command.add("-loglevel");
        command.add("warning");
        command.add("-protocol_whitelist");
        command.add("crypto,https,tls,tcp");
        command.add("-rw_timeout");
        command.add("15000000");
        command.add("-ss");
        command.add(formatSeconds(startMicros));
        command.add("-readrate");
        command.add(Double.toString(playbackRate));
        command.add("-i");
        command.add(media.streamUri().toASCIIString());
        command.add("-map");
        command.add("0:v:0");
        command.add("-an");
        command.add("-vf");
        command.add("scale=" + outputWidth + ":" + outputHeight
                + ":force_original_aspect_ratio=decrease,pad=" + outputWidth + ":" + outputHeight + ":(ow-iw)/2:(oh-ih)/2:black");
        command.add("-r");
        command.add(Double.toString(targetFrameRate));
        command.add("-fps_mode");
        command.add("cfr");
        if (singleFrame) {
            command.add("-frames:v");
            command.add("1");
        }
        command.add("-f");
        command.add("rawvideo");
        command.add("-pix_fmt");
        command.add("rgba");
        command.add("pipe:1");
        return command;
    }

    private void decode(
            FutureRun expectedRun,
            long startMicros,
            double playbackRate,
            double sourceFrameRate,
            VideoFrameSink sink) {
        int frameSize = Math.multiplyExact(Math.multiplyExact(outputWidth, outputHeight), 4);
        double outputFrameRate = Math.min(60.0, sourceFrameRate * playbackRate);
        long frameDurationMicros = Math.max(1, Math.round(1_000_000.0 / outputFrameRate));
        long frameIndex = 0;
        try (InputStream input = expectedRun.process().getInputStream()) {
            while (!expectedRun.cancelled().get()) {
                byte[] pixels = readFrame(input, frameSize);
                long presentationTime = startMicros + Math.round(frameIndex * frameDurationMicros * playbackRate);
                sink.submit(new VideoFrame(
                        outputWidth,
                        outputHeight,
                        outputWidth * 4,
                        PixelFormat.RGBA8,
                        presentationTime,
                        ByteBuffer.wrap(pixels),
                        () -> { }));
                frameIndex++;
                synchronized (lock) {
                    if (run == expectedRun) {
                        positionMicros = presentationTime;
                    }
                }
            }
        } catch (EOFException endOfStream) {
            synchronized (lock) {
                if (run == expectedRun) {
                    positionMicros = startMicros + Math.round(frameIndex * frameDurationMicros * playbackRate);
                    playing = false;
                    process = null;
                    run = null;
                }
            }
        } catch (IOException error) {
            if (!expectedRun.cancelled().get()) {
                synchronized (lock) {
                    if (run == expectedRun) {
                        playing = false;
                        process = null;
                        run = null;
                    }
                }
            }
        } finally {
            expectedRun.process().destroy();
        }
    }

    private static byte[] readFrame(InputStream input, int frameSize) throws IOException {
        byte[] frame = new byte[frameSize];
        int offset = 0;
        while (offset < frame.length) {
            int read = input.read(frame, offset, frame.length - offset);
            if (read < 0) {
                throw new EOFException();
            }
            offset += read;
        }
        return frame;
    }

    private void drainErrors(FutureRun expectedRun) {
        byte[] buffer = new byte[4096];
        try (InputStream error = expectedRun.process().getErrorStream()) {
            while (!expectedRun.cancelled().get() && error.read(buffer) != -1) {
                // Drain bounded OS pipe. Media URLs and diagnostics are intentionally not logged.
            }
        } catch (IOException ignored) {
        }
    }

    private void stopProcessLocked() {
        FutureRun current = run;
        run = null;
        Process currentProcess = process;
        process = null;
        if (current != null) {
            current.cancelled().set(true);
        }
        if (currentProcess != null) {
            currentProcess.destroy();
            try {
                if (!currentProcess.waitFor(2, TimeUnit.SECONDS)) {
                    currentProcess.destroyForcibly();
                }
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                currentProcess.destroyForcibly();
            }
        }
    }

    private void requireReady() {
        requireOpen();
        if (media == null || frameSink == null || probe == null) {
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
        synchronized (lock) {
            if (closed) {
                return;
            }
            closed = true;
            playing = false;
            stopProcessLocked();
            if (frameSink != null) {
                frameSink.close();
                frameSink = null;
            }
            media = null;
            probe = null;
        }
        executor.shutdownNow();
    }

    private static String formatSeconds(long micros) {
        return String.format(java.util.Locale.ROOT, "%.6f", micros / 1_000_000.0);
    }

    private static final class FutureRun {
        private final Process process;
        private final AtomicBoolean cancelled = new AtomicBoolean();

        private FutureRun(Process process) {
            this.process = process;
        }

        Process process() {
            return process;
        }

        AtomicBoolean cancelled() {
            return cancelled;
        }
    }

    private static final class NamedThreadFactory implements ThreadFactory {
        private int index;

        @Override
        public synchronized Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "vidscreen-ffmpeg-" + index++);
            thread.setDaemon(true);
            return thread;
        }
    }
}
