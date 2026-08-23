package com.botmaker.sdk.internal.capture;

import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.shared.capture.GenericWindow;

import java.awt.image.BufferedImage;

/**
 * A {@link CaptureSource} narrowed to a rectangle of another one — what
 * {@link CaptureSource#region(Rect)} returns.
 *
 * <p>This was an anonymous class inside {@code region(Rect)} until 1.1.0. It is named now for one reason: an
 * anonymous class can implement only the one type it is written as, and a region of a window has to be both a
 * {@code CaptureSource} and {@link WindowBacked} (keys go to the window the region sits in — a key has no
 * sub-rectangle to land in). It lives in {@code internal} because it is only ever <em>returned</em>; a bot
 * writes {@code source.region(rect)}, never this name.
 */
public final class RegionSource implements CaptureSource, WindowBacked {

    private final CaptureSource parent;
    private final Rect sub;

    public RegionSource(CaptureSource parent, Rect sub) {
        this.parent = parent;
        this.sub = sub;
    }

    @Override
    public BufferedImage capture() {
        BufferedImage img = parent.capture();
        if (img == null) return null;
        int x = Math.max(0, sub.x());
        int y = Math.max(0, sub.y());
        int w = Math.min(sub.width(), img.getWidth() - x);
        int h = Math.min(sub.height(), img.getHeight() - y);
        if (w <= 0 || h <= 0) return null;
        return img.getSubimage(x, y, w, h);
    }

    @Override
    public Point origin() {
        Point o = parent.origin();
        return new Point(o.x() + Math.max(0, sub.x()), o.y() + Math.max(0, sub.y()));
    }

    @Override
    public void click(Point p) {
        // Route the click to the underlying surface so a region of an emulator still taps via ADB.
        parent.click(p);
    }

    @Override
    public boolean isPresent() {
        return parent.isPresent();
    }

    @Override
    public boolean hasWindowIdentity() {
        return parent.hasWindowIdentity();
    }

    @Override
    public CaptureSource base() {
        return parent.base();
    }

    @Override
    public Rect subRegion() {
        Rect pr = parent.subRegion();
        int bx = (pr != null ? pr.x() : 0) + Math.max(0, sub.x());
        int by = (pr != null ? pr.y() : 0) + Math.max(0, sub.y());
        return new Rect(bx, by, sub.width(), sub.height());
    }

    @Override
    public GenericWindow targetWindow() {
        // A region of a window still targets that window for keyboard input (keys have no sub-rect). A region
        // of anything else is not window-backed, and WindowBacked.of answers null for it.
        return WindowBacked.of(parent);
    }
}
