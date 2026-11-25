// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.internal.capture;

import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.capture.Source;
import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.shared.capture.GenericWindow;

import java.awt.image.BufferedImage;

/**
 * A source that is whatever {@link Source#current()} answers at the moment it is used: every member asks it again,
 * so a value of it works as a source in a running bot. {@link CurrentSource} and {@link AutomaticSource} are the two
 * values, which differ only in what the bot's Java says.
 */
public abstract sealed class AmbientSource implements CaptureSource, WindowBacked
        permits CurrentSource, AutomaticSource {

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
    public void drag(Point from, Point to, long durationMs) {
        Source.current().drag(from, to, durationMs);
    }

    @Override
    public void scroll(Point at, int notches) {
        Source.current().scroll(at, notches);
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

    /** Every value of one kind is the same value. */
    @Override
    public boolean equals(Object other) {
        return other != null && other.getClass() == getClass();
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
