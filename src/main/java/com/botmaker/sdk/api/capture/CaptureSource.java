package com.botmaker.sdk.api.capture;

import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.internal.capture.Desktop;
import com.botmaker.sdk.internal.capture.Monitor;
import com.botmaker.sdk.internal.capture.NamedWindow;
import com.botmaker.sdk.internal.capture.RegionSource;
import com.botmaker.sdk.api.interaction.Mouse;

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
 *
 * <p><b>Curated for the palette</b> (see {@code @Palette}): seven of thirteen. What is offered is the whole of
 * how a bot <em>builds</em> a source — the four factories and the two {@link #region(Rect) region} narrowings,
 * each of which hands back another {@code CaptureSource} the editor can hold — plus {@link #isPresent()}, the
 * one question a bot asks of a source it already has.
 *
 * <p>The six hidden ones divide into three pairs, and none of them is deprecated in any sense.
 * {@link #capture()} and {@link #origin()} go together: the first returns a {@code BufferedImage}, which is not
 * a type the editor can declare a variable of, and the second's only documented use is adding it to a match
 * from the first — so offering either alone would be offering half of an operation the palette cannot finish.
 * {@link #base()} and {@link #subRegion()} are labelled observability hooks above and are read by
 * {@code internal.observe}, not by bots. {@link #hasWindowIdentity()} and {@link #click(Point)} are
 * <b>implementor surface</b>: they exist so a new kind of source can describe and route itself — {@code click}
 * says so in as many words, "the single seam that lets the whole vision→click pipeline target an emulator" —
 * and the supported bot path is {@code Emulators.use()} followed by plain {@code Mouse}. An override point is
 * not a menu entry; that is the same verdict {@code Emulators} reached from the other direction.
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
        Mouse.click(p);
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

    /**
     * Creates a capture source from the project's default capture target configuration.
     * This allows bots to use the same capture source that Studio configured for the project.
     *
     * @return a capture source based on the project's default capture target, or the current source if not configured
     */
    static CaptureSource fromProjectDefault() {
        CaptureSource source = com.botmaker.sdk.internal.config.ProjectDefaults.source();
        return source != null ? source : Source.current();
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
        return new RegionSource(this, sub);
    }

    /** {@link #region(Rect)} from raw coordinates within this source. */
    default CaptureSource region(int x, int y, int width, int height) {
        return region(new Rect(x, y, width, height));
    }

    // --- Observability hooks (used by internal.observe to describe the surface + searched region) ---

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
    //
    // There is deliberately no targetWindow() here any more. It returned botmaker-shared's GenericWindow, and
    // shared is freely breakable by design while this interface is under contract from 1.1.0 — so the one
    // method put a type nobody promises to keep into the surface everybody relies on. It is
    // internal.capture.WindowBacked now: the sources that resolve to a desktop window implement it, keyboard
    // routing asks WindowBacked.of(source), and nothing in api names GenericWindow. See that interface for the
    // full account.
}
