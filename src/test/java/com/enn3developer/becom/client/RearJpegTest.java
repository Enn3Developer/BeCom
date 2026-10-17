package com.enn3developer.becom.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.enn3developer.becom.logic.Tunables;

/** Rear pictures fit the per-client cap at Max's size, and come back the right way up. */
class RearJpegTest {

    private static final int RED = 0xFFFF0000;
    private static final int BLUE = 0xFF0000FF;

    /** A picture like a view of the world: sky over ground, with some detail, bottom row first. */
    private static int[] view(int width, int height, long seed) {
        Random random = new Random(seed);
        int[] pixels = new int[width * height];
        for (int row = 0; row < height; row++) {
            for (int x = 0; x < width; x++) {
                boolean ground = row < height / 2;
                int base = ground ? 0x3F6F2F : 0x7FAFEF;
                int grain = random.nextInt(24);
                pixels[row * width + x] = 0xFF000000 | base + (grain << 16) + (grain << 8) + grain;
            }
        }
        return pixels;
    }

    @Test
    void maxSizedViewFitsTheDefaultCap() {
        Tunables t = Tunables.defaults();
        byte[] jpeg = RearJpeg.encode(
            view(t.rearWidthMax(), t.rearHeightMax(), 1),
            t.rearWidthMax(),
            t.rearHeightMax(),
            t.rearMaxBytes());
        assertNotNull(jpeg);
        assertTrue(jpeg.length <= t.rearMaxBytes(), jpeg.length + " bytes");
        BufferedImage back = RearJpeg.decode(jpeg);
        assertEquals(t.rearWidthMax(), back.getWidth());
        assertEquals(t.rearHeightMax(), back.getHeight());
    }

    @Test
    void theBottomRowReadFromOpenGlEndsUpAtTheBottom() {
        int width = 16, height = 16;
        int[] pixels = new int[width * height];
        for (int i = 0; i < pixels.length; i++) pixels[i] = i < width * height / 2 ? RED : BLUE;
        BufferedImage back = RearJpeg.decode(RearJpeg.encode(pixels, width, height, 8192));
        assertTrue((back.getRGB(8, height - 2) & 0xFF0000) > 0x800000, "red at the bottom");
        assertTrue((back.getRGB(8, 1) & 0xFF) > 0x80, "blue at the top");
    }

    @Test
    void aPictureThatCannotFitIsDropped() {
        assertNull(RearJpeg.encode(view(192, 108, 2), 192, 108, 64));
        assertNull(RearJpeg.decode(new byte[] { 1, 2, 3 }));
    }

    /**
     * A client can send anything: a few hundred bytes whose header claims a huge picture, cut short after the header.
     * Java's reader would make the whole picture before finding the data missing, so it's refused unread.
     */
    @Test
    void aHeaderClaimingAHugePictureIsRefusedBeforeDecoding() {
        byte[] jpeg = RearJpeg.encode(view(16, 16, 3), 16, 16, 8192);
        int frame = marker(jpeg, 0xC0);
        int scan = marker(jpeg, 0xDA);
        // SOF0: marker, length, precision, then the height and the width.
        int huge = 4000;
        jpeg[frame + 5] = (byte) (huge >> 8);
        jpeg[frame + 6] = (byte) huge;
        jpeg[frame + 7] = (byte) (huge >> 8);
        jpeg[frame + 8] = (byte) huge;
        int scanHeader = ((jpeg[scan + 2] & 0xFF) << 8 | jpeg[scan + 3] & 0xFF) + 2;
        byte[] cut = java.util.Arrays.copyOf(jpeg, scan + scanHeader + 4);
        BufferedImage back = RearJpeg.decode(cut);
        assertTrue(
            back == null || back.getWidth() <= Tunables.MAX_REAR_WIDTH && back.getHeight() <= Tunables.MAX_REAR_HEIGHT,
            back == null ? "" : back.getWidth() + "x" + back.getHeight());
    }

    private static int marker(byte[] jpeg, int code) {
        for (int i = 0; i + 1 < jpeg.length; i++) {
            if ((jpeg[i] & 0xFF) == 0xFF && (jpeg[i + 1] & 0xFF) == code) return i;
        }
        throw new AssertionError("no marker " + Integer.toHexString(code));
    }
}
