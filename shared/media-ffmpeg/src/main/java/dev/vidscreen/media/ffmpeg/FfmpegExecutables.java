package dev.vidscreen.media.ffmpeg;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class FfmpegExecutables {
    private static final String FFMPEG_PROPERTY = "vidscreen.ffmpeg";
    private static final String FFPROBE_PROPERTY = "vidscreen.ffprobe";
    private static final String FFMPEG_ENVIRONMENT = "VIDSCREEN_FFMPEG";
    private static final String FFPROBE_ENVIRONMENT = "VIDSCREEN_FFPROBE";

    private final Path ffmpeg;
    private final Path ffprobe;

    public FfmpegExecutables(Path ffmpeg, Path ffprobe) {
        this.ffmpeg = requireExecutable(ffmpeg, "ffmpeg");
        this.ffprobe = requireExecutable(ffprobe, "ffprobe");
    }

    public static Optional<FfmpegExecutables> discover() {
        Path ffmpeg = locate(FFMPEG_PROPERTY, FFMPEG_ENVIRONMENT, "ffmpeg");
        Path ffprobe = locate(FFPROBE_PROPERTY, FFPROBE_ENVIRONMENT, "ffprobe");
        if (ffmpeg == null || ffprobe == null) {
            return Optional.empty();
        }
        return Optional.of(new FfmpegExecutables(ffmpeg, ffprobe));
    }

    public Path ffmpeg() {
        return ffmpeg;
    }

    public Path ffprobe() {
        return ffprobe;
    }

    private static Path locate(String property, String environment, String command) {
        String configured = trimToNull(System.getProperty(property));
        if (configured == null) {
            configured = trimToNull(System.getenv(environment));
        }
        if (configured != null) {
            Path path = Paths.get(configured).toAbsolutePath().normalize();
            return isExecutable(path) ? path : null;
        }

        String pathVariable = trimToNull(System.getenv("PATH"));
        if (pathVariable == null) {
            return null;
        }
        for (String directory : pathVariable.split(java.util.regex.Pattern.quote(File.pathSeparator))) {
            String normalizedDirectory = trimToNull(directory);
            if (normalizedDirectory == null) {
                continue;
            }
            for (String fileName : executableNames(command)) {
                Path candidate = Paths.get(normalizedDirectory).resolve(fileName).toAbsolutePath().normalize();
                if (isExecutable(candidate)) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private static List<String> executableNames(String command) {
        List<String> names = new ArrayList<String>();
        names.add(command);
        if (isWindows()) {
            names.add(command + ".exe");
        }
        return names;
    }

    private static Path requireExecutable(Path path, String name) {
        if (path == null) {
            throw new NullPointerException(name);
        }
        Path normalized = path.toAbsolutePath().normalize();
        if (!isExecutable(normalized)) {
            throw new IllegalArgumentException(name + " executable is not a readable file");
        }
        return normalized;
    }

    private static boolean isExecutable(Path path) {
        return Files.isRegularFile(path) && Files.isReadable(path) && (isWindows() || Files.isExecutable(path));
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
