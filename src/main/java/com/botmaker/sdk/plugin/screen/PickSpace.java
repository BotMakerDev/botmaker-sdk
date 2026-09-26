package com.botmaker.sdk.plugin.screen;

import com.botmaker.sdk.api.capture.CaptureSource;

import java.awt.Rectangle;
import java.lang.reflect.Executable;
import java.util.Arrays;
import java.util.Optional;

/**
 * Which numbers a screen pick writes (2026-09-26). A bare {@code Point} is absolute desktop pixels in
 * {@code Mouse.click(Point)} and relative to a source in {@code Mouse.click(source, x, y)}, so the pick asks the
 * call it sits in. The overlay always reports a pick inside the frame it showed (its top-left is {@code 0,0});
 * this adds the frame's desktop origin back when the call wants desktop pixels.
 */
public enum PickSpace {

    /** Inside the chosen surface: its top-left is {@code 0,0}, so the numbers survive the window moving. */
    RELATIVE,

    /** Desktop pixels, as a call with no source to be relative to reads them. */
    ABSOLUTE;

    /**
     * {@link #RELATIVE} when the call takes a {@link CaptureSource} or is a method of one; {@link #ABSOLUTE} for
     * any other call, and for a slot whose call did not resolve (a bare {@code Point} with nothing to be
     * relative to is a desktop point). A value with no slot — a Parameters row — is {@link #RELATIVE}: the
     * field is read by whatever the bot hands it to, and the row's label says which surface it was picked on.
     */
    public static PickSpace of(Optional<Executable> call, boolean isSlot) {
        if (!isSlot) return RELATIVE;
        if (call.isEmpty()) return ABSOLUTE;
        Executable executable = call.get();
        boolean onSource = CaptureSource.class.isAssignableFrom(executable.getDeclaringClass());
        boolean takesSource = Arrays.stream(executable.getParameterTypes())
                .anyMatch(CaptureSource.class::isAssignableFrom);
        return onSource || takesSource ? RELATIVE : ABSOLUTE;
    }

    /** {@code [x, y]} picked inside the frame, in this space. */
    public int[] point(int[] framePoint, Rectangle frameBounds) {
        if (this == RELATIVE || frameBounds == null) return framePoint.clone();
        return new int[]{framePoint[0] + frameBounds.x, framePoint[1] + frameBounds.y};
    }

    /** {@code [x, y, width, height]} picked inside the frame, in this space; the size never moves. */
    public int[] region(int[] frameRegion, Rectangle frameBounds) {
        int[] corner = point(new int[]{frameRegion[0], frameRegion[1]}, frameBounds);
        return new int[]{corner[0], corner[1], frameRegion[2], frameRegion[3]};
    }
}
