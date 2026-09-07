package dev.vidscreen.media.ffmpeg.nativeapi;

import org.bytedeco.javacpp.Loader;

import static org.bytedeco.ffmpeg.global.avcodec.avcodec_version;
import static org.bytedeco.ffmpeg.global.avformat.avformat_version;
import static org.bytedeco.ffmpeg.global.avutil.avutil_version;
import static org.bytedeco.ffmpeg.global.swresample.swresample_version;
import static org.bytedeco.ffmpeg.global.swscale.swscale_version;

public final class NativeFfmpegRuntime {
    private NativeFfmpegRuntime() {
    }

    public static boolean isAvailable() {
        try {
            load();
            return true;
        } catch (LinkageError error) {
            return false;
        } catch (RuntimeException error) {
            return false;
        }
    }

    public static void requireAvailable() {
        try {
            load();
        } catch (LinkageError error) {
            throw new NativeFfmpegException("The VidScreen native media runtime is unavailable for "
                    + Loader.getPlatform(), error);
        } catch (RuntimeException error) {
            throw new NativeFfmpegException("The VidScreen native media runtime could not load for "
                    + Loader.getPlatform(), error);
        }
    }

    public static String platform() {
        return Loader.getPlatform();
    }

    private static void load() {
        avutil_version();
        avcodec_version();
        avformat_version();
        swscale_version();
        swresample_version();
    }
}
