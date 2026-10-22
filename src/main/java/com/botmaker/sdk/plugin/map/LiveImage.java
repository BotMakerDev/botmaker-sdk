package com.botmaker.sdk.plugin.map;

import com.botmaker.shared.capture.FrameSink;
import javafx.scene.image.Image;
import javafx.scene.image.PixelBuffer;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.nio.IntBuffer;

/**
 * The live board's game, two images drawn in turn (2026-10-22): a grab writes each frame straight into the array of
 * the one not shown ({@link #pixels}, off the FX thread), and {@link #show} swaps the two on it. A new {@link Image}
 * per frame was a frame's worth of garbage and an upload each time (2026-10-18); a copy into a spare array and another
 * into the image's buffer were two more passes over each frame.
 *
 * <p>One frame is in flight at a time: the frame loop writes the back slot only while no show is pending, and a show
 * swaps the slots before the loop may write again, so the slot shown is never written. A game's frame is opaque, so
 * its ARGB pixels are the premultiplied ones the buffer holds.
 */
final class LiveImage implements FrameSink {

    /** One image and the array its buffer wraps. */
    static final class Slot {
        private int[] pixels = new int[0];
        private int width;
        private int height;
        private PixelBuffer<IntBuffer> buffer;
        private WritableImage image;

        int width() {
            return width;
        }

        int height() {
            return height;
        }
    }

    /** What the frame loop writes. */
    private Slot back = new Slot();
    /** What the board shows; on the FX thread. */
    private Slot front = new Slot();

    /** The back slot's array, sized for a frame of {@code width × height}; off the FX thread, while no show is pending. */
    @Override
    public int[] pixels(int width, int height) {
        int size = width * height;
        if (size == 0) throw new IllegalArgumentException("An empty frame: nothing to draw.");
        if (back.pixels.length != size) back.pixels = new int[size];
        back.width = width;
        back.height = height;
        return back.pixels;
    }

    /** The frame just written, copied, for a read that outlives it; on the frame loop's thread, before its show. */
    BufferedImage taken() {
        return copy(back);
    }

    /**
     * The frame just written drawn, in place of the one shown: the same image as last time this slot was shown when
     * the frame's size held, else a new one. On the FX thread, after {@link #pixels}.
     */
    Image show() {
        Slot shown = flip();
        if (shown.image == null || shown.buffer.getWidth() != shown.width || shown.buffer.getHeight() != shown.height
                || shown.buffer.getBuffer().array() != shown.pixels) {
            shown.buffer = new PixelBuffer<>(shown.width, shown.height, IntBuffer.wrap(shown.pixels),
                    PixelFormat.getIntArgbPreInstance());
            shown.image = new WritableImage(shown.buffer);
        } else {
            shown.buffer.updateBuffer(b -> null);
        }
        return shown.image;
    }

    /** The slot just written made the one shown, the other the one written next; returns the shown. */
    Slot flip() {
        Slot shown = back;
        back = front;
        front = shown;
        return shown;
    }

    /** Whether a frame was shown yet. On the FX thread. */
    boolean showing() {
        return front.width != 0;
    }

    /** The frame shown, copied, lossless; null before the first. On the FX thread. */
    BufferedImage shown() {
        return showing() ? copy(front) : null;
    }

    private static BufferedImage copy(Slot slot) {
        BufferedImage copy = new BufferedImage(slot.width, slot.height, BufferedImage.TYPE_INT_RGB);
        int[] into = ((DataBufferInt) copy.getRaster().getDataBuffer()).getData();
        System.arraycopy(slot.pixels, 0, into, 0, slot.width * slot.height);
        return copy;
    }
}
