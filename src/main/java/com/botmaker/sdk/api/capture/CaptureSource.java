package com.botmaker.sdk.api.capture;

import com.botmaker.sdk.api.Point;
import com.botmaker.sdk.api.Rect;

import java.awt.image.BufferedImage;

/**
 * <em>Where</em> the vision layer looks — exactly one of three things: a {@link Window}, a single
 * {@link #monitor(int) monitor}, or the whole {@link #desktop() desktop}. Every matcher
 * ({@code ImageFinder}/{@code ImageClicker}/{@code ImageWaiter}) takes a {@code CaptureSource} instead of a
 * loose screen rectangle, so a template is matched — and clicked — in the source's own coordinate space
 * (even off-screen or on a second monitor) without duplicating any matching logic: the matcher runs on
 * {@link #capture()} and converts in-image match coordinates to absolute screen coordinates by adding
 * {@link #origin()}.
 *
 * <p>A search <em>region</em> is not a separate concept — it is a {@link Rect} <em>of</em> a source: narrow
 * any of the three with {@link #region(Rect)} to get a sub-source that only captures (and therefore only
 * matches within) that rectangle. Regions compose, so
 * {@code CaptureSource.window("Game").region(topBar)} is itself just another {@code CaptureSource}.
 */
public interface CaptureSource {

    /** Pixels of this source. May return {@code null} if the capture failed. */
    BufferedImage capture();

    /**
     * Absolute screen coordinate of pixel (0,0) of the image returned by {@link #capture()}.
     * Add this to an in-image match location to obtain an absolute, clickable coordinate.
     */
    Point origin();

    /**
     * Whether this source currently exists / can be captured. The whole {@link #desktop()} and a
     * {@link #monitor(int) monitor} are always present, so this defaults to {@code true}; a window
     * source ({@link #window(String)}) overrides it to report whether a matching window is open right
     * now. Used by {@link com.botmaker.sdk.api.launch.Game} to tell if a game is already running.
     */
    default boolean isPresent() {
        return true;
    }

    /**
     * Whether {@link #isPresent()} actually means something for this source. The whole {@link #desktop()}
     * and a {@link #monitor(int) monitor} always answer {@code true} to {@code isPresent()} because they
     * always exist — so a caller asking "is the target already up?" would get a permanent, meaningless
     * "yes". Only a source tied to a specific application window ({@link #window(String)} and a concrete
     * {@link Window}) reports a real presence, and only those override this to {@code true}.
     *
     * <p>Used by {@link com.botmaker.sdk.api.launch.LaunchTarget#startIfNotRunning()} to decide whether the
     * ambient source can answer "already running", or whether it must fall back to a process-name probe.
     */
    default boolean hasWindowIdentity() {
        return false;
    }

    /**
     * Sends a primary click at absolute point {@code p} — the location a matcher produced on <em>this</em>
     * source. The default is a real desktop click ({@link com.botmaker.sdk.api.interaction.Mouse#click(Point)}),
     * which is correct for the on-screen sources (desktop / monitor / window). A source whose pixels are
     * <em>not</em> on the desktop — an emulator captured over ADB — overrides this to inject the click through
     * its own channel (e.g. {@code adb input tap}); because such a source reports {@link #origin()} as
     * {@code (0,0)}, {@code p} is already a coordinate in that source's own pixel space. This is the single
     * seam that lets the whole vision→click pipeline target an emulator without any matcher change.
     */
    default void click(Point p) {
        com.botmaker.sdk.api.interaction.Mouse.click(p);
    }

    // --- The three canonical sources ---

    /** The whole virtual desktop (all monitors). The ultimate fallback source for every matcher. */
    static CaptureSource desktop() {
        return new Desktop();
    }

    /**
     * A single monitor (0-based {@code index} into the OS screen-device list), so a bot can match against
     * just one screen on a multi-monitor desktop. An out-of-range index falls back to the whole desktop.
     */
    static CaptureSource monitor(int index) {
        return new Monitor(index);
    }

    /**
     * A capture source that targets the first window whose title contains {@code titleSubstring}
     * (case-insensitive). The window is resolved <em>lazily on every use</em>, so the source survives
     * the window not existing yet (e.g. before the game launches) and re-binds if the window moves or
     * reopens: {@link #capture()} returns {@code null} while it is absent, and {@link #isPresent()}
     * reports whether it is currently open.
     */
    static CaptureSource window(String titleSubstring) {
        return new NamedWindow(titleSubstring);
    }

    // --- Region: a Rect that belongs to THIS source ---

    /**
     * A sub-source that only captures the {@code sub} rectangle within this source's pixel space (its
     * top-left is {@code (0,0)} of {@link #capture()}). Matches are still reported in absolute screen
     * coordinates because {@link #origin()} shifts by {@code sub}'s top-left. Because it actually crops the
     * captured image, a region also restricts (and speeds up) the search area — not just the reported
     * coordinates. The rectangle is clamped to the source's bounds.
     */
    default CaptureSource region(Rect sub) {
        CaptureSource parent = this;
        return new CaptureSource() {
            @Override
            public BufferedImage capture() {
                BufferedImage img = parent.capture();
                if (img == null) return null;
                int x = Math.max(0, sub.x);
                int y = Math.max(0, sub.y);
                int w = Math.min(sub.width, img.getWidth() - x);
                int h = Math.min(sub.height, img.getHeight() - y);
                if (w <= 0 || h <= 0) return null;
                return img.getSubimage(x, y, w, h);
            }

            @Override
            public Point origin() {
                Point o = parent.origin();
                return new Point(o.x + Math.max(0, sub.x), o.y + Math.max(0, sub.y));
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
                int bx = (pr != null ? pr.x : 0) + Math.max(0, sub.x);
                int by = (pr != null ? pr.y : 0) + Math.max(0, sub.y);
                return new Rect(bx, by, sub.width, sub.height);
            }

            @Override
            public com.botmaker.shared.capture.GenericWindow targetWindow() {
                // A region of a window still targets that window for keyboard input (keys have no sub-rect).
                return parent.targetWindow();
            }
        };
    }

    /** {@link #region(Rect)} from raw coordinates within this source. */
    default CaptureSource region(int x, int y, int width, int height) {
        return region(new Rect(x, y, width, height));
    }

    // --- Observability hooks (used by api.observe to describe the surface + searched region) ---

    /**
     * The underlying whole-surface source (a {@link Window} or the screen) this source draws from, unwrapping
     * any {@link #region(Rect)} narrowing. Defaults to {@code this}; only region sub-sources override it.
     */
    default CaptureSource base() {
        return this;
    }

    /**
     * The searched rectangle within {@link #base()} (in that surface's pixel space), or {@code null} when
     * this source captures the whole surface. Only region sub-sources return a non-null value.
     */
    default Rect subRegion() {
        return null;
    }

    // --- Input targeting ---

    /**
     * The native OS window this source represents, when it is a real on-screen application window — the seam
     * that lets {@link com.botmaker.sdk.api.interaction.Keyboard#press(CaptureSource, com.botmaker.sdk.api.interaction.Key)
     * Keyboard} deliver keys to <em>this</em> window specifically (the keyboard counterpart of {@link #click(Point)}).
     * Defaults to {@code null}: the whole {@link #desktop()}, a {@link #monitor(int) monitor}, a
     * {@link #window(String) window} that isn't open yet, and an emulator all have no single desktop window to
     * route keys to, so keyboard input falls back to the global focused-window path. Only a resolved
     * {@link Window} (and a {@link #region(Rect) region} of one) returns a non-null handle.
     */
    default com.botmaker.shared.capture.GenericWindow targetWindow() {
        return null;
    }
}
