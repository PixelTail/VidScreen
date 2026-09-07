package dev.vidscreen.media.ffmpeg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FfmpegExecutablesTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void discoversExplicitlyConfiguredExecutables() throws Exception {
        Path ffmpeg = Files.createFile(temporaryDirectory.resolve("ffmpeg.exe"));
        Path ffprobe = Files.createFile(temporaryDirectory.resolve("ffprobe.exe"));
        String previousFfmpeg = System.getProperty("vidscreen.ffmpeg");
        String previousFfprobe = System.getProperty("vidscreen.ffprobe");
        try {
            System.setProperty("vidscreen.ffmpeg", ffmpeg.toString());
            System.setProperty("vidscreen.ffprobe", ffprobe.toString());

            FfmpegExecutables executables = FfmpegExecutables.discover().orElseThrow(AssertionError::new);

            assertEquals(ffmpeg.toAbsolutePath().normalize(), executables.ffmpeg());
            assertEquals(ffprobe.toAbsolutePath().normalize(), executables.ffprobe());
        } finally {
            restore("vidscreen.ffmpeg", previousFfmpeg);
            restore("vidscreen.ffprobe", previousFfprobe);
        }
    }

    @Test
    void rejectsMissingExplicitExecutable() {
        String previousFfmpeg = System.getProperty("vidscreen.ffmpeg");
        String previousFfprobe = System.getProperty("vidscreen.ffprobe");
        try {
            System.setProperty("vidscreen.ffmpeg", temporaryDirectory.resolve("missing-ffmpeg").toString());
            System.setProperty("vidscreen.ffprobe", temporaryDirectory.resolve("missing-ffprobe").toString());
            assertTrue(!FfmpegExecutables.discover().isPresent());
        } finally {
            restore("vidscreen.ffmpeg", previousFfmpeg);
            restore("vidscreen.ffprobe", previousFfprobe);
        }
    }

    private static void restore(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }
}
