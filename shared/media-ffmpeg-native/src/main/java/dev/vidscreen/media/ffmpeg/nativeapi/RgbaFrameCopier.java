package dev.vidscreen.media.ffmpeg.nativeapi;

import java.nio.Buffer;
import java.nio.ByteBuffer;

import org.bytedeco.javacv.Frame;

import dev.vidscreen.media.PixelFormat;
import dev.vidscreen.media.VideoFrame;

final class RgbaFrameCopier {
    private RgbaFrameCopier() {
    }

    static VideoFrame copy(Frame sourceFrame, int outputWidth, int outputHeight, long presentationTimeMicros) {
        if (sourceFrame == null || sourceFrame.image == null || sourceFrame.image.length == 0) {
            throw new NativeFfmpegException("The decoder returned no image data");
        }
        if (sourceFrame.imageDepth != Frame.DEPTH_UBYTE && sourceFrame.imageDepth != Frame.DEPTH_BYTE) {
            throw new NativeFfmpegException("The decoder returned an unsupported image depth");
        }
        Buffer image = sourceFrame.image[0];
        if (!(image instanceof ByteBuffer)) {
            throw new NativeFfmpegException("The decoder returned a non-byte image buffer");
        }
        int sourceWidth = sourceFrame.imageWidth;
        int sourceHeight = sourceFrame.imageHeight;
        int sourceStride = sourceFrame.imageStride;
        if (sourceWidth < 1 || sourceHeight < 1 || sourceWidth > outputWidth || sourceHeight > outputHeight) {
            throw new NativeFfmpegException("The decoded frame dimensions exceed the configured output");
        }
        int sourceRowBytes = Math.multiplyExact(sourceWidth, 4);
        if (sourceStride < sourceRowBytes) {
            throw new NativeFfmpegException("The decoded RGBA frame has an invalid row stride");
        }

        ByteBuffer source = ((ByteBuffer) image).duplicate();
        int sourceBase = source.position();
        long required = (long) sourceBase + (long) (sourceHeight - 1) * sourceStride + sourceRowBytes;
        if (required > source.limit()) {
            throw new NativeFfmpegException("The decoded RGBA buffer is truncated");
        }

        int outputStride = Math.multiplyExact(outputWidth, 4);
        byte[] outputBytes = new byte[Math.multiplyExact(outputStride, outputHeight)];
        for (int alpha = 3; alpha < outputBytes.length; alpha += 4) {
            outputBytes[alpha] = (byte) 0xff;
        }
        ByteBuffer output = ByteBuffer.wrap(outputBytes);
        int offsetX = (outputWidth - sourceWidth) / 2;
        int offsetY = (outputHeight - sourceHeight) / 2;
        for (int row = 0; row < sourceHeight; row++) {
            ByteBuffer sourceRow = source.duplicate();
            int sourceOffset = sourceBase + row * sourceStride;
            sourceRow.position(sourceOffset);
            sourceRow.limit(sourceOffset + sourceRowBytes);
            output.position((row + offsetY) * outputStride + offsetX * 4);
            output.put(sourceRow);
        }
        output.clear();
        return new VideoFrame(
                outputWidth,
                outputHeight,
                outputStride,
                PixelFormat.RGBA8,
                presentationTimeMicros,
                output,
                new Runnable() {
                    @Override
                    public void run() {
                    }
                });
    }

    static Dimensions fit(int sourceWidth, int sourceHeight, int outputWidth, int outputHeight) {
        if (sourceWidth < 1 || sourceHeight < 1 || outputWidth < 1 || outputHeight < 1) {
            throw new IllegalArgumentException("Frame dimensions must be positive");
        }
        if ((long) sourceWidth * outputHeight >= (long) sourceHeight * outputWidth) {
            int height = Math.max(1, (int) Math.round((double) sourceHeight * outputWidth / sourceWidth));
            return new Dimensions(outputWidth, Math.min(outputHeight, height));
        }
        int width = Math.max(1, (int) Math.round((double) sourceWidth * outputHeight / sourceHeight));
        return new Dimensions(Math.min(outputWidth, width), outputHeight);
    }

    static final class Dimensions {
        private final int width;
        private final int height;

        Dimensions(int width, int height) {
            this.width = width;
            this.height = height;
        }

        int width() {
            return width;
        }

        int height() {
            return height;
        }
    }
}
