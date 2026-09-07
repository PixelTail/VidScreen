package dev.vidscreen.media.ytdlp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class YtDlpExecutableTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void discoversExplicitlyConfiguredExecutable() throws Exception {
        Path executable = Files.createFile(temporaryDirectory.resolve("yt-dlp.exe"));
        assertTrue(executable.toFile().setExecutable(true));
        String previous = System.getProperty("vidscreen.ytdlp");
        try {
            System.setProperty("vidscreen.ytdlp", executable.toString());
            assertEquals(
                    executable.toAbsolutePath().normalize(),
                    YtDlpExecutable.discover().orElseThrow(AssertionError::new));
        } finally {
            restore(previous);
        }
    }

    @Test
    void rejectsMissingExplicitExecutable() {
        String previous = System.getProperty("vidscreen.ytdlp");
        try {
            System.setProperty("vidscreen.ytdlp", temporaryDirectory.resolve("missing-ytdlp").toString());
            assertTrue(!YtDlpExecutable.discover().isPresent());
        } finally {
            restore(previous);
        }
    }

    private static void restore(String value) {
        if (value == null) {
            System.clearProperty("vidscreen.ytdlp");
        } else {
            System.setProperty("vidscreen.ytdlp", value);
        }
    }
}
