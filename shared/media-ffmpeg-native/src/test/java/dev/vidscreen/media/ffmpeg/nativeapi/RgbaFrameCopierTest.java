package dev.vidscreen.media.ffmpeg.nativeapi;

import java.nio.ByteBuffer;

import org.bytedeco.javacv.Frame;
import org.junit.jupiter.api.Test;

import dev.vidscreen.media.VideoFrame;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class RgbaFrameCopierTest {
    @Test
    void preservesAspectRatioInsideOutputBounds() {
        RgbaFrameCopier.Dimensions wide = RgbaFrameCopier.fit(1920, 1080, 640, 480);
        assertEquals(640, wide.width());
        assertEquals(360, wide.height());

        RgbaFrameCopier.Dimensions tall = RgbaFrameCopier.fit(1080, 1920, 640, 360);
        assertEquals(203, tall.width());
        assertEquals(360, tall.height());
    }

    @Test
    void copiesPaddedRgbaRowsIntoOpaqueLetterbox() {
        ByteBuffer pixels = ByteBuffer.allocate(12);
        pixels.put(new byte[] {
                10, 20, 30, 40,
                50, 60, 70, 80,
                99, 99, 99, 99
        });
        pixels.flip();

        Frame source = new Frame();
        source.image = new java.nio.Buffer[] { pixels };
        source.imageWidth = 2;
        source.imageHeight = 1;
        source.imageDepth = Frame.DEPTH_UBYTE;
        source.imageChannels = 4;
        source.imageStride = 12;

        VideoFrame copied = RgbaFrameCopier.copy(source, 4, 3, 1234L);
        try {
            assertEquals(16, copied.rowStride());
            assertEquals(1234L, copied.presentationTimeMicros());
            byte[] actual = new byte[48];
            copied.pixels().get(actual);
            byte[] expected = opaqueBlack(48);
            System.arraycopy(new byte[] {
                    10, 20, 30, 40,
                    50, 60, 70, 80
            }, 0, expected, 20, 8);
            assertArrayEquals(expected, actual);
        } finally {
            copied.close();
        }
    }

    private static byte[] opaqueBlack(int length) {
        byte[] pixels = new byte[length];
        for (int alpha = 3; alpha < pixels.length; alpha += 4) {
            pixels[alpha] = (byte) 0xff;
        }
        return pixels;
    }
}
