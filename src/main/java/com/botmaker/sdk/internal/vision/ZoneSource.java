package com.botmaker.sdk.internal.vision;

import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.geometry.Size;
import com.botmaker.sdk.api.geometry.Zone;
import com.botmaker.sdk.internal.capture.RegionSource;
import com.botmaker.shared.capture.GenericWindow;
import com.botmaker.sdk.internal.capture.WindowBacked;

import java.awt.image.BufferedImage;

/**
 * {@code source.region(Zone)}: the zone placed on {@code source}'s whole surface at each capture, not once, so a
 * source kept in a field follows the window it resolves to and the size it shows (2026-10-11). A capture measures
 * the surface from the frame it crops, so it costs one grab, as {@code region(Rect)} does.
 */
public final class ZoneSource implements CaptureSource, WindowBacked {

    private final CaptureSource of;
    private final Zone zone;

    public ZoneSource(CaptureSource of, Zone zone) {
        this.of = of;
        this.zone = zone;
    }

    /** The zone it narrows to, in the bot's pixels. */
    Zone zone() {
        return zone;
    }

    @Override
    public BufferedImage capture() {
        CaptureSource whole = of.base();
        BufferedImage frame = whole.capture();
        if (frame == null) return null;
        Rect sub = Places.inSource(zone, BotScreen.size().orElse(null), new Size(frame.getWidth(), frame.getHeight()));
        return new RegionSource(new Captured(whole, frame), sub).capture();
    }

    @Override
    public Point origin() {
        return now().origin();
    }

    @Override
    public void click(Point p) {
        of.base().click(p);
    }

    @Override
    public boolean isPresent() {
        return of.isPresent();
    }

    @Override
    public boolean hasWindowIdentity() {
        return of.hasWindowIdentity();
    }

    @Override
    public CaptureSource base() {
        return of.base();
    }

    @Override
    public Rect subRegion() {
        return now().subRegion();
    }

    @Override
    public GenericWindow targetWindow() {
        return WindowBacked.of(of.base());
    }

    /** The zone as a plain region of the surface as it shows now. */
    private RegionSource now() {
        CaptureSource whole = of.base();
        return new RegionSource(whole, Places.inSource(zone, whole));
    }

    @Override
    public String toString() {
        return of + ".region(" + zone + ")";
    }

    /** {@code whole} with one frame already grabbed, so the crop does not grab a second. */
    private record Captured(CaptureSource whole, BufferedImage frame) implements CaptureSource {
        @Override
        public BufferedImage capture() {
            return frame;
        }

        @Override
        public Point origin() {
            return whole.origin();
        }
    }
}
