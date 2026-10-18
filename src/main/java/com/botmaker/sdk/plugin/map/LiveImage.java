package com.botmaker.sdk.plugin.map;

import javafx.scene.image.Image;
import javafx.scene.image.PixelBuffer;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.awt.image.SinglePixelPackedSampleModel;
import java.nio.IntBuffer;

/**
 * The live board's game, one image drawn again in place each frame (2026-10-18): a new {@link Image} per frame
 * at 30 a second was a frame's worth of garbage and an upload each time. The frame's pixels are copied off the FX
 * thread into one spare array ({@link #take}), then into the image's own buffer on it ({@link #show}); one frame is
 * in flight at a time, so the spare is never written while it is read.
 *
 * <p>A game's frame is opaque, so its ARGB pixels are the premultiplied ones the buffer holds.
 */
final class LiveImage {

    private int[] spare = new int[0];
    private int spareWidth;
    private int spareHeight;

    // On the FX thread from here.
    private IntBuffer pixels;
    private PixelBuffer<IntBuffer> buffer;
    private WritableImage image;

    /** Copies {@code frame}'s pixels into the spare array; off the FX thread, while no frame is in flight. */
    void take(BufferedImage frame) {
        spareWidth = frame.getWidth();
        spareHeight = frame.getHeight();
        int size = spareWidth * spareHeight;
        if (size == 0) throw new IllegalArgumentException("An empty frame: nothing to draw.");
        if (spare.length != size) spare = new int[size];
        argb(frame, spare);
    }

    /**
     * The spare array drawn: the same image as last time when the frame's size held, else a new one. On the FX
     * thread, after {@link #take}.
     */
    Image show() {
        if (image == null || image.getWidth() != spareWidth || image.getHeight() != spareHeight) {
            pixels = IntBuffer.allocate(spareWidth * spareHeight);
            buffer = new PixelBuffer<>(spareWidth, spareHeight, pixels, PixelFormat.getIntArgbPreInstance());
            image = new WritableImage(buffer);
        }
        buffer.updateBuffer(b -> {
            pixels.put(0, spare);
            return null;
        });
        return image;
    }

    /**
     * {@code frame}'s pixels as opaque ARGB into {@code into}, its width times its height long: the raster's own ints
     * copied whole when it holds them so, else read through its colour model.
     */
    static void argb(BufferedImage frame, int[] into) {
        int w = frame.getWidth();
        int h = frame.getHeight();
        int type = frame.getType();
        if ((type == BufferedImage.TYPE_INT_RGB || type == BufferedImage.TYPE_INT_ARGB)
                && frame.getRaster().getDataBuffer() instanceof DataBufferInt ints
                && ints.getNumBanks() == 1 && ints.getOffset() == 0 && frame.getRaster().getParent() == null
                && frame.getSampleModel() instanceof SinglePixelPackedSampleModel packed
                && packed.getScanlineStride() == w) {
            System.arraycopy(ints.getData(), 0, into, 0, w * h);
        } else {
            frame.getRGB(0, 0, w, h, into, 0, w);
        }
        for (int i = 0; i < w * h; i++) into[i] |= 0xFF000000;
    }
}
