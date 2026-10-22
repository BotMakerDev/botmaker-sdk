package com.botmaker.sdk.plugin.map;

import com.botmaker.shared.capture.FrameGrab;

import java.awt.image.BufferedImage;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Where the live board reads the game, found but not opened: the frame loop opens it, and keeps it open while the
 * feed found again each second names the same place (2026-10-22), since a window's grab holds its pixmap and a
 * shared-memory segment.
 *
 * @param where what names the place; equal while it is the same one
 * @param opens the grab of it, opened on the frame loop's thread and closed there
 */
record LiveFeed(Object where, Supplier<FrameGrab> opens) {

    /** A feed of the frames {@code reads} makes, nothing kept open: a place of its own each time it is found. */
    static LiveFeed of(Supplier<Optional<BufferedImage>> reads) {
        return new LiveFeed(new Object(), () -> FrameGrab.of(() -> reads.get().orElse(null)));
    }

    /** Whether {@code other} reads where this one does, so the grab open on this one serves it. */
    boolean samePlace(LiveFeed other) {
        return other != null && Objects.equals(where, other.where);
    }
}
