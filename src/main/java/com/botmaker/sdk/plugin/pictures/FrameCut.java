package com.botmaker.sdk.plugin.pictures;

import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.geometry.Size;

/**
 * Where a picture was cut: the size of the frame it came out of, and the box it took from that frame, in the
 * frame's own pixels. What its constant is written with — {@code .at(Rect)}, and {@code .in(Size)} when the frame
 * is not the bot's screen size — so the bot's Java holds it and no file beside the picture does (2026-10-11).
 *
 * @param frame the whole frame's size; null when unknown
 * @param place the box cut, in the frame's pixels; null when unknown (an object cut, an imported file)
 */
public record FrameCut(Size frame, Rect place) {

    private static final FrameCut UNKNOWN = new FrameCut(null, null);

    /** A picture whose frame is not known: a file brought in from outside. */
    public static FrameCut unknown() {
        return UNKNOWN;
    }

    /** Cut out of a {@code width}×{@code height} frame at {@code place}; a size of 0 is unknown. */
    public static FrameCut of(int width, int height, Rect place) {
        return new FrameCut(width > 0 && height > 0 ? new Size(width, height) : null, place);
    }
}
