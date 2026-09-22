package dev.vidscreen.media.ffmpeg;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

final class Ffprobe {
    private static final int MAX_OUTPUT_BYTES = 64 * 1024;

    private final Path executable;

    Ffprobe(Path executable) {
        this.executable = executable;
    }

    ProbeResult inspect(String source) throws IOException, InterruptedException {
        List<String> command = new ArrayList<String>();
        command.add(executable.toString());
        command.add("-protocol_whitelist");
        command.add("crypto,https,tls,tcp");
        command.add("-rw_timeout");
        command.add("15000000");
        command.add("-v");
        command.add("error");
        command.add("-select_streams");
        command.add("v:0");
        command.add("-show_entries");
        command.add("stream=width,height,avg_frame_rate:format=duration");
        command.add("-of");
        command.add("default=noprint_wrappers=1:nokey=1");
        command.add(source);

        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        FutureTask<byte[]> outputTask = new FutureTask<byte[]>(() -> {
            try (InputStream input = process.getInputStream()) {
                return readBounded(input);
            }
        });
        Thread outputReader = new Thread(outputTask, "vidscreen-ffprobe-output");
        outputReader.setDaemon(true);
        outputReader.start();

        if (!process.waitFor(30, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            outputTask.cancel(true);
            throw new IOException("ffprobe timed out");
        }

        byte[] output;
        try {
            output = outputTask.get();
        } catch (ExecutionException error) {
            Throwable cause = error.getCause();
            if (cause instanceof IOException) {
                throw (IOException) cause;
            }
            throw new IOException("Could not read ffprobe output", cause);
        } finally {
            process.getInputStream().close();
        }
        if (process.exitValue() != 0) {
            throw new IOException("ffprobe failed with exit code " + process.exitValue());
        }

        String[] lines = new String(output, StandardCharsets.UTF_8).trim().split("\\R");
        if (lines.length < 3) {
            throw new IOException("ffprobe returned incomplete video metadata");
        }
        try {
            int width = Integer.parseInt(lines[0].trim());
            int height = Integer.parseInt(lines[1].trim());
            double frameRate = parseRate(lines[2].trim());
            long durationMillis = lines.length < 4 ? -1L : parseDurationMillis(lines[3].trim());
            return new ProbeResult(width, height, frameRate, durationMillis);
        } catch (RuntimeException error) {
            throw new IOException("ffprobe returned invalid video metadata", error);
        }
    }

    private static long parseDurationMillis(String value) {
        if (value.isEmpty() || "N/A".equalsIgnoreCase(value)) {
            return -1L;
        }
        double seconds = Double.parseDouble(value);
        if (!Double.isFinite(seconds) || seconds <= 0) {
            return -1L;
        }
        double millis = seconds * 1_000.0;
        if (millis >= Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        return Math.max(1L, Math.round(millis));
    }

    private static double parseRate(String value) {
        int separator = value.indexOf('/');
        double rate;
        if (separator < 0) {
            rate = Double.parseDouble(value);
        } else {
            double numerator = Double.parseDouble(value.substring(0, separator));
            double denominator = Double.parseDouble(value.substring(separator + 1));
            rate = denominator == 0 ? 0 : numerator / denominator;
        }
        if (!Double.isFinite(rate) || rate <= 0 || rate > 240) {
            throw new IllegalArgumentException("Invalid frame rate: " + value);
        }
        return rate;
    }

    private static byte[] readBounded(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = input.read(buffer)) != -1) {
            if (output.size() + read > MAX_OUTPUT_BYTES) {
                throw new IOException("ffprobe output exceeded limit");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    static final class ProbeResult {
        private final int width;
        private final int height;
        private final double frameRate;
        private final long durationMillis;

        ProbeResult(int width, int height, double frameRate, long durationMillis) {
            if (width < 1 || height < 1 || width > 65_536 || height > 65_536) {
                throw new IllegalArgumentException("Invalid source dimensions");
            }
            if (durationMillis == 0 || durationMillis < -1) {
                throw new IllegalArgumentException("Invalid media duration");
            }
            this.width = width;
            this.height = height;
            this.frameRate = frameRate;
            this.durationMillis = durationMillis;
        }

        int width() {
            return width;
        }

        int height() {
            return height;
        }

        double frameRate() {
            return frameRate;
        }

        long durationMillis() {
            return durationMillis;
        }
    }
}
