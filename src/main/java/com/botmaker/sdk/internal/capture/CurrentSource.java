package com.botmaker.sdk.internal.capture;

import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.capture.Source;
import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.shared.capture.GenericWindow;

import java.awt.image.BufferedImage;

/**
 * The ambient source as a value: whatever {@link Source#current()} answers at the moment it is used.
 *
 * <p>It is what {@code Source.current()} is read back as when the host reads a bot's Java, and what a fresh
 * {@code CaptureSource} declaration starts as. Freezing the answer into a concrete source would stop the
 * declaration following the project's source when that changes. Every member asks {@link Source#current()}
 * again, so this one also works as a source in a running bot.
 */
public final class CurrentSource implements CaptureSource, WindowBacked {

    @Override
    public BufferedImage capture() {
        return Source.current().capture();
    }

    @Override
    public Point origin() {
        return Source.current().origin();
    }

    @Override
    public boolean isPresent() {
        return Source.current().isPresent();
    }

    @Override
    public boolean hasWindowIdentity() {
        return Source.current().hasWindowIdentity();
    }

    @Override
    public void click(Point p) {
        Source.current().click(p);
    }

    @Override
    public CaptureSource base() {
        return Source.current().base();
    }

    @Override
    public Rect subRegion() {
        return Source.current().subRegion();
    }

    @Override
    public GenericWindow targetWindow() {
        return WindowBacked.of(Source.current());
    }

    /** Every ambient source is the same value. */
    @Override
    public boolean equals(Object other) {
        return other instanceof CurrentSource;
    }

    @Override
    public int hashCode() {
        return CurrentSource.class.hashCode();
    }

    @Override
    public String toString() {
        return "Source.current()";
    }
}
