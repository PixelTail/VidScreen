package dev.vidscreen.runtime;

import org.junit.jupiter.api.Test;

import dev.vidscreen.media.ffmpeg.nativeapi.NativeFfmpegRuntime;

import static org.junit.jupiter.api.Assertions.assertTrue;

class NativeFfmpegRuntimeTest {
    @Test
    void loadsBundledNativeLibrariesOnAdvertisedBuildPlatform() {
        NativeFfmpegRuntime.requireAvailable();
        assertTrue(NativeFfmpegRuntime.isAvailable());
    }
}
