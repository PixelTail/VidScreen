package dev.vidscreen.media.ytdlp;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import dev.vidscreen.media.MediaKind;
import dev.vidscreen.media.MediaRequest;
import dev.vidscreen.media.MediaResolver;
import dev.vidscreen.media.MediaUrlPolicy;
import dev.vidscreen.media.ResolvedMedia;

public final class YtDlpMediaResolver implements MediaResolver {
    private static final int MAX_OUTPUT_BYTES = 1024 * 1024;
    private static final long TIMEOUT_SECONDS = 60;

    private final String id;
    private final Path executable;
    private final MediaUrlPolicy urlPolicy;
    private final List<String> hostSuffixes;

    public YtDlpMediaResolver(
            String id,
            Path executable,
            MediaUrlPolicy urlPolicy,
            String... hostSuffixes) {
        this.id = requireId(id);
        this.executable = Objects.requireNonNull(executable, "executable").toAbsolutePath().normalize();
        this.urlPolicy = Objects.requireNonNull(urlPolicy, "urlPolicy");
        if (hostSuffixes.length == 0) {
            throw new IllegalArgumentException("At least one provider host suffix is required");
        }
        List<String> normalized = new ArrayList<String>(hostSuffixes.length);
        for (String suffix : hostSuffixes) {
            String value = Objects.requireNonNull(suffix, "hostSuffix").toLowerCase(Locale.ROOT);
            if (value.isEmpty() || value.startsWith(".") || value.endsWith(".")) {
                throw new IllegalArgumentException("Invalid provider host suffix");
            }
            normalized.add(value);
        }
        this.hostSuffixes = Collections.unmodifiableList(normalized);
    }

    public static YtDlpMediaResolver bilibili(Path executable, MediaUrlPolicy urlPolicy) {
        return new YtDlpMediaResolver("bilibili", executable, urlPolicy, "bilibili.com", "b23.tv");
    }

    public static YtDlpMediaResolver youtube(Path executable, MediaUrlPolicy urlPolicy) {
        return new YtDlpMediaResolver("youtube", executable, urlPolicy, "youtube.com", "youtu.be");
    }

    public static YtDlpMediaResolver twitch(Path executable, MediaUrlPolicy urlPolicy) {
        return new YtDlpMediaResolver("twitch", executable, urlPolicy, "twitch.tv");
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public boolean supports(URI source) {
        String host = source.getHost();
        if (host == null) {
            return false;
        }
        String normalized = host.toLowerCase(Locale.ROOT);
        for (String suffix : hostSuffixes) {
            if (normalized.equals(suffix) || normalized.endsWith("." + suffix)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public CompletionStage<ResolvedMedia> resolve(MediaRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                URI source = urlPolicy.validate(request.source());
                if (!supports(source)) {
                    throw new YtDlpException("Source host is not supported by the " + id + " resolver");
                }
                Extraction extraction = extract(source, request.preferredHeight());
                URI stream = urlPolicy.validate(URI.create(extraction.streamUrl()));
                return new ResolvedMedia(stream, mediaKind(extraction), null);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new CompletionException(new YtDlpException("yt-dlp was interrupted", error));
            } catch (IOException | RuntimeException error) {
                throw new CompletionException(error);
            } catch (Exception error) {
                throw new CompletionException(new YtDlpException("Could not validate resolved media", error));
            }
        });
    }

    private Extraction extract(URI source, int preferredHeight) throws IOException, InterruptedException {
        List<String> command = new ArrayList<String>();
        command.add(executable.toString());
        command.addAll(Arrays.asList(
                "--ignore-config",
                "--no-playlist",
                "--no-warnings",
                "--no-progress",
                "--no-color",
                "--socket-timeout", "15",
                "--retries", "1",
                "--extractor-retries", "1",
                "--format", "best[height<=" + preferredHeight + "]/best",
                "--print", "%(protocol)s\t%(is_live)s\t%(url)s",
                source.toASCIIString()));

        Process process = new ProcessBuilder(command).start();
        FutureTask<byte[]> stdoutTask = reader(process.getInputStream(), true);
        FutureTask<byte[]> stderrTask = reader(process.getErrorStream(), false);
        startReader(stdoutTask, "vidscreen-ytdlp-output");
        startReader(stderrTask, "vidscreen-ytdlp-errors");

        if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            stdoutTask.cancel(true);
            stderrTask.cancel(true);
            throw new YtDlpException("yt-dlp timed out");
        }

        byte[] output;
        try {
            output = stdoutTask.get();
        } catch (ExecutionException error) {
            Throwable cause = error.getCause();
            if (cause instanceof IOException) {
                throw (IOException) cause;
            }
            throw new IOException("Could not read yt-dlp output", cause);
        } finally {
            process.getInputStream().close();
            process.getErrorStream().close();
        }
        if (process.exitValue() != 0) {
            throw new YtDlpException("yt-dlp failed with exit code " + process.exitValue());
        }
        return parse(output);
    }

    private static FutureTask<byte[]> reader(InputStream input, boolean retain) {
        return new FutureTask<byte[]>(() -> {
            try (InputStream stream = input) {
                ByteArrayOutputStream output = retain ? new ByteArrayOutputStream() : null;
                byte[] buffer = new byte[4096];
                int total = 0;
                int read;
                while ((read = stream.read(buffer)) != -1) {
                    total += read;
                    if (total > MAX_OUTPUT_BYTES) {
                        throw new IOException("yt-dlp output exceeded limit");
                    }
                    if (output != null) {
                        output.write(buffer, 0, read);
                    }
                }
                return output == null ? new byte[0] : output.toByteArray();
            }
        });
    }

    private static void startReader(FutureTask<byte[]> task, String name) {
        Thread thread = new Thread(task, name);
        thread.setDaemon(true);
        thread.start();
    }

    static Extraction parse(byte[] output) {
        String text = new String(output, StandardCharsets.UTF_8).trim();
        String[] lines = text.split("\\R");
        for (String line : lines) {
            if (line.trim().isEmpty()) {
                continue;
            }
            String[] fields = line.split("\\t", 3);
            if (fields.length != 3 || fields[0].trim().isEmpty() || fields[2].trim().isEmpty()) {
                throw new YtDlpException("yt-dlp returned invalid media metadata");
            }
            return new Extraction(fields[0].trim(), "true".equalsIgnoreCase(fields[1].trim()), fields[2].trim());
        }
        throw new YtDlpException("yt-dlp returned no playable media");
    }

    static MediaKind mediaKind(Extraction extraction) {
        if (extraction.live()) {
            return MediaKind.LIVE;
        }
        String protocol = extraction.protocol().toLowerCase(Locale.ROOT);
        if (protocol.contains("m3u8")) {
            return MediaKind.HLS;
        }
        if (protocol.contains("dash")) {
            return MediaKind.DASH;
        }
        return MediaKind.MP4;
    }

    private static String requireId(String id) {
        String normalized = Objects.requireNonNull(id, "id").trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9_-]{1,32}")) {
            throw new IllegalArgumentException("Invalid resolver ID");
        }
        return normalized;
    }

    static final class Extraction {
        private final String protocol;
        private final boolean live;
        private final String streamUrl;

        private Extraction(String protocol, boolean live, String streamUrl) {
            this.protocol = protocol;
            this.live = live;
            this.streamUrl = streamUrl;
        }

        String protocol() {
            return protocol;
        }

        boolean live() {
            return live;
        }

        String streamUrl() {
            return streamUrl;
        }
    }
}
