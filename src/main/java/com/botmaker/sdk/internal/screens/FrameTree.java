package com.botmaker.sdk.internal.screens;

import com.botmaker.shared.emulator.tree.UiTree;

import java.awt.image.BufferedImage;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * The tree read for the last frame looked at, and its matcher: every element a check looks for on one frame is found
 * in one read, and a frame is never matched against a tree read for another. One entry, keyed by the frame's identity.
 */
public final class FrameTree {

    private record Entry(BufferedImage frame, Optional<ElementMatch.Matcher> matcher) {}

    private Entry last;

    /** The tree for {@code frame}: the one read for it already, else {@code read} once. */
    public synchronized Optional<ElementMatch.Matcher> on(BufferedImage frame, Supplier<Optional<UiTree>> read) {
        if (last != null && last.frame() == frame) return last.matcher();
        Entry now = new Entry(frame, read.get().map(ElementMatch::in));
        last = now;
        return now.matcher();
    }
}
