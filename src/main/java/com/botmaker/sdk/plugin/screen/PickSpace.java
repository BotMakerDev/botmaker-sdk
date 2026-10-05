package com.botmaker.sdk.plugin.screen;

import com.botmaker.sdk.api.capture.CaptureSource;

import java.awt.Rectangle;
import java.lang.reflect.Executable;
import java.util.Arrays;
import java.util.Optional;

/**
 * Which numbers a screen pick writes. A bare {@code Point} is absolute desktop pixels in
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
     * {@link #RELATIVE} when the call takes a {@link CaptureSource} ({@code Mouse.click(source, x, y)}) or cuts
     * a sub-region out of one ({@code source.region(rect)}); {@link #ABSOLUTE} for any other call, and for a
     * slot whose call did not resolve (a bare {@code Point} with nothing to be relative to is a desktop point).
     * Being declared on {@code CaptureSource} is not enough: {@code source.click(p)} takes the absolute point a
     * matcher produced. A value with no slot — a Parameters row — is {@link #RELATIVE}: the field is read by
     * whatever the bot hands it to, and the row's label says which surface it was picked on.
     */
    public static PickSpace of(Optional<Executable> call, boolean isSlot) {
        if (!isSlot) return RELATIVE;
        if (call.isEmpty()) return ABSOLUTE;
        Executable executable = call.get();
        boolean subRegion = CaptureSource.class.isAssignableFrom(executable.getDeclaringClass())
                && executable.getName().equals("region");
        boolean takesSource = Arrays.stream(executable.getParameterTypes())
                .anyMatch(CaptureSource.class::isAssignableFrom);
        return subRegion || takesSource ? RELATIVE : ABSOLUTE;
    }

    /**
     * Where {@code frame}'s top-left is on the desktop, or {@code null} when it is nowhere: an emulator's frame
     * arrives over ADB and its bounds only say where to draw it, so there is no desktop origin to add.
     */
    public static Rectangle origin(EditorFrame frame) {
        return frame.onScreen() ? frame.bounds() : null;
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
