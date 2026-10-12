package com.enn3developer.becom.client;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;

/**
 * The rear camera's pictures as JPEG (DESIGN.md 10.1): compressed small enough for the server's size cap, trying lower
 * qualities until one fits, and read back for showing. Runs on the video worker, never the render thread.
 */
public final class RearJpeg {

    // JPEG qualities to try, best first.
    private static final float[] QUALITIES = { 0.75f, 0.6f, 0.45f, 0.3f, 0.2f };

    private RearJpeg() {}

    /**
     * A JPEG of {@code pixels}, ARGB rows bottom first as OpenGL reads them, no larger than {@code maxBytes}; null
     * when even the lowest quality doesn't fit.
     */
    public static byte[] encode(int[] pixels, int width, int height, int maxBytes) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int row = 0; row < height; row++) {
            image.setRGB(0, height - 1 - row, width, 1, pixels, row * width, width);
        }
        for (float quality : QUALITIES) {
            byte[] jpeg = jpeg(image, quality);
            if (jpeg != null && jpeg.length <= maxBytes) return jpeg;
        }
        return null;
    }

    /** The picture in {@code jpeg}, or null when it isn't one. */
    public static BufferedImage decode(byte[] jpeg) {
        try {
            return ImageIO.read(new ByteArrayInputStream(jpeg));
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static byte[] jpeg(BufferedImage image, float quality) {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) return null;
        ImageWriter writer = writers.next();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (MemoryCacheImageOutputStream stream = new MemoryCacheImageOutputStream(out)) {
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);
            writer.setOutput(stream);
            writer.write(null, new IIOImage(image, null, null), param);
        } catch (IOException e) {
            return null;
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }
}
