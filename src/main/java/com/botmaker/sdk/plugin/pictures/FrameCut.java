package com.botmaker.sdk.plugin.pictures;

import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.geometry.Size;
import com.botmaker.sdk.api.vision.ImageTemplate;

/**
 * Where a picture was cut: the size of the frame it came out of, the box it took from that frame, in the
 * frame's own pixels, and how many frames its file holds. What its constant is written with — {@code .at(Rect)},
 * {@code .in(Size)} when the frame is not the bot's screen size, and {@code .frames(n)} for a moving one — so the
 * bot's Java holds it and no file beside the picture does (2026-10-11).
 *
 * @param frame  the whole frame's size; null when unknown
 * @param place  the box cut, in the frame's pixels; null when unknown (an object cut, an imported file)
 * @param frames how many frames its file holds side by side ({@link FrameStrip}); 1 for a still picture
 */
public record FrameCut(Size frame, Rect place, int frames) {

    private static final FrameCut UNKNOWN = new FrameCut(null, null);

    public FrameCut {
        if (frames < 1) throw new IllegalArgumentException("A picture has at least one frame, not " + frames + ".");
    }

    /** A still picture cut at {@code place} out of a {@code frame}. */
    public FrameCut(Size frame, Rect place) {
        this(frame, place, 1);
    }

    /** A picture whose frame is not known: a file brought in from outside. */
    public static FrameCut unknown() {
        return UNKNOWN;
    }

    /** Cut out of a {@code width}×{@code height} frame at {@code place}; a size of 0 is unknown. */
    public static FrameCut of(int width, int height, Rect place) {
        return new FrameCut(width > 0 && height > 0 ? new Size(width, height) : null, place);
    }

    /** Where {@code held} says it was cut, and how many frames it holds. */
    public static FrameCut of(ImageTemplate held) {
        return new FrameCut(held.frame(), held.place(), held.frames());
    }

    /** This cut holding {@code count} frames. */
    public FrameCut withFrames(int count) {
        return new FrameCut(frame, place, count);
    }

    /** {@code picture} cut here: at its place, in its frame, holding its frames. */
    public ImageTemplate onto(ImageTemplate picture) {
        ImageTemplate placed = place == null ? picture : picture.at(place);
        return (frame == null ? placed : placed.in(frame)).frames(frames);
    }
}
