package com.botmaker.sdk.plugin.map;

import com.botmaker.shared.capture.FrameSink;
import javafx.scene.image.Image;
import javafx.scene.image.PixelBuffer;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.nio.IntBuffer;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The live board's game, four images in turn (2026-10-22), as a game's own swap chain: the capture thread writes
 * each frame straight into the array of the one it holds ({@link #pixels}) and {@link #publish}es it, the FX pulse
 * {@link #take}s the newest published and {@link #show}s it, a third waits between the two, and the fourth, shown
 * last, waits for the render thread to be done with it. Neither side ever waits on the other: a frame the pulse did
 * not take in time is replaced by the next, never queued.
 *
 * <p>A new {@link Image} per frame was a frame's worth of garbage and an upload each time (2026-10-18); a copy into a
 * spare array and another into the image's buffer were two more passes over each frame. A game's frame is opaque, so
 * its ARGB pixels are the premultiplied ones the buffer holds.
 */
final class LiveImage implements FrameSink {

    /** Set on {@link #middle} while it holds a frame the pulse has not taken. */
    private static final int FRESH = 4;

    /** One image and the array its buffer wraps. */
    private static final class Slot {
        private int[] pixels = new int[0];
        private int width;
        private int height;
        private PixelBuffer<IntBuffer> buffer;
        private WritableImage image;
    }

    private final Slot[] slots = {new Slot(), new Slot(), new Slot(), new Slot()};
    /** The slot between the two sides, by index, with {@link #FRESH} while it holds a frame not taken yet. */
    private final AtomicInteger middle = new AtomicInteger(1);
    /** The slot the capture thread writes; its own. */
    private int back;
    /** The slot the board shows; the FX thread's own. */
    private int front = 2;
    /**
     * The slot shown before {@link #front}, still the FX thread's: the render thread may still be uploading it when
     * the next pulse's timers run, as a pulse's timers come before the scene sync that waits for the last render. It
     * goes back to the capture a pulse later, once that render is surely done.
     */
    private int retired = 3;

    /** The written slot's array, sized for a frame of {@code width × height}; on the capture thread. */
    @Override
    public int[] pixels(int width, int height) {
        int size = width * height;
        if (size == 0) throw new IllegalArgumentException("An empty frame: nothing to draw.");
        Slot slot = slots[back];
        if (slot.pixels.length != size) slot.pixels = new int[size];
        slot.width = width;
        slot.height = height;
        return slot.pixels;
    }

    /** The frame just written, copied, for a read that outlives it; on the capture thread, before {@link #publish}. */
    BufferedImage taken() {
        return copy(slots[back]);
    }

    /** The frame just written handed to the pulse, in place of one it did not take; on the capture thread. */
    void publish() {
        back = middle.getAndSet(back | FRESH) & ~FRESH;
    }

    /**
     * The newest frame published made the one shown, if one came since the last; on the FX thread. Only this side
     * clears {@link #FRESH}, so a frame seen fresh is still there to take.
     */
    boolean take() {
        if ((middle.get() & FRESH) == 0) return false;
        int shownLast = front;
        front = middle.getAndSet(retired) & ~FRESH;
        retired = shownLast;
        return true;
    }

    /**
     * The frame taken drawn: the same image as last time this slot was shown when the frame's size held, else a new
     * one. On the FX thread, after {@link #take}.
     */
    Image show() {
        Slot shown = slots[front];
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

    /** Whether a frame was taken yet. On the FX thread. */
    boolean showing() {
        return slots[front].width != 0;
    }

    /** The frame shown, copied, lossless; null before the first. On the FX thread. */
    BufferedImage shown() {
        return showing() ? copy(slots[front]) : null;
    }

    private static BufferedImage copy(Slot slot) {
        BufferedImage copy = new BufferedImage(slot.width, slot.height, BufferedImage.TYPE_INT_RGB);
        int[] into = ((DataBufferInt) copy.getRaster().getDataBuffer()).getData();
        System.arraycopy(slot.pixels, 0, into, 0, slot.width * slot.height);
        return copy;
    }
}
