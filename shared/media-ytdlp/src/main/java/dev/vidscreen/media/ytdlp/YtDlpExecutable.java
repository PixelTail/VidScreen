package dev.vidscreen.media.ytdlp;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class YtDlpExecutable {
    private static final String PROPERTY = "vidscreen.ytdlp";
    private static final String ENVIRONMENT = "VIDSCREEN_YTDLP";

    private YtDlpExecutable() {
    }

    public static Optional<Path> discover() {
        String configured = trimToNull(System.getProperty(PROPERTY));
        if (configured == null) {
            configured = trimToNull(System.getenv(ENVIRONMENT));
        }
        if (configured != null) {
            Path path = Paths.get(configured).toAbsolutePath().normalize();
            return isExecutable(path) ? Optional.of(path) : Optional.<Path>empty();
        }

        String pathVariable = trimToNull(System.getenv("PATH"));
        if (pathVariable == null) {
            return Optional.empty();
        }
        for (String directory : pathVariable.split(java.util.regex.Pattern.quote(File.pathSeparator))) {
            String normalizedDirectory = trimToNull(directory);
            if (normalizedDirectory == null) {
                continue;
            }
            for (String fileName : executableNames()) {
                Path candidate = Paths.get(normalizedDirectory).resolve(fileName).toAbsolutePath().normalize();
                if (isExecutable(candidate)) {
                    return Optional.of(candidate);
                }
            }
        }
        return Optional.empty();
    }

    private static List<String> executableNames() {
        List<String> names = new ArrayList<String>();
        names.add("yt-dlp");
        if (isWindows()) {
            names.add("yt-dlp.exe");
        }
        return names;
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
