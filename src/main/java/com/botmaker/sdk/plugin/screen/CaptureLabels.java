package com.botmaker.sdk.plugin.screen;

import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.emulator.EmulatorSource;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.internal.capture.CurrentSource;
import com.botmaker.sdk.internal.capture.Monitor;
import com.botmaker.sdk.internal.capture.NamedWindow;
import com.botmaker.sdk.internal.capture.RegionSource;
import com.botmaker.shared.emulator.EmulatorInstances;

import java.util.Objects;

/**
 * What a {@link CaptureSource} is called on screen — a settings row, a menu entry, a toolbar button, the
 * line under a thumbnail — and which surface it reads.
 *
 * <h2>Here rather than on {@code CaptureSource}, and that is the same line as everywhere else</h2>
 *
 * <p>A capture source is what a <em>bot</em> reads pixels from; what to call one in a window is the
 * editor's business. Putting {@code longLabel()} on the interface would put three English sentences under
 * the SDK's never-delete contract and on every bot's classpath, for a question no bot asks.
 *
 * <p><b>A source is named by what it is</b>: the bot's own Java has nowhere to put a nickname. Every label
 * below is derived, so two surfaces cannot disagree about how a source is named.
 *
 * <h2>A region is its surface, narrowed</h2>
 *
 * <p>{@code CaptureSource.region(window("G"), rect)} reads window G and crops it, so every question about
 * <em>which surface</em> — {@link #windowTitle}, {@link #monitorIndex}, {@link #emulatorName},
 * {@link #isDesktop} — is answered for {@link #whole} it, and {@link #region} says the crop. Until 2026-09-28
 * a region was answered as the whole desktop, so a narrowed project grabbed the desktop in every editor.
 *
 * <h2>Both label methods are total, and absent means the desktop</h2>
 *
 * <p>A {@code null} source is the whole desktop everywhere, which is what an unset default already meant
 * and what {@link com.botmaker.sdk.api.capture.Source#current()} answers before anything sets it.
 */
public final class CaptureLabels {

    private CaptureLabels() {}

    /**
     * The long label — a settings row, a menu entry, the line under a thumbnail.
     *
     * <p>The form says what it is: <em>Screen 2</em>, <em>Window: Diablo IV</em>, <em>Whole desktop (all
     * monitors)</em>, then <em> — region 200×100 at (10, 20)</em> when narrowed. An emulator is captioned by
     * {@link EmulatorInstances#captionFor}, because the same instance name may be a phone or an emulator and
     * only a live scan can say which.
     */
    public static String longLabel(CaptureSource source) {
        CaptureSource whole = whole(source);
        String label;
        if (whole instanceof CurrentSource) label = "Project default";
        else if (whole instanceof Monitor monitor) label = "Screen " + (monitor.index() + 1);
        else if (whole instanceof NamedWindow window) {
            label = "Window: " + (blank(window.titleSubstring()) ? "(any)" : window.titleSubstring());
        } else if (whole instanceof EmulatorSource emulator) {
            label = EmulatorInstances.captionFor(emulator.instanceName());
        } else label = "Whole desktop (all monitors)";
        Rect region = region(source);
        return region == null ? label : "%s — region %d×%d at (%d, %d)"
                .formatted(label, region.width(), region.height(), region.x(), region.y());
    }

    /**
     * The short label — a toolbar button, a slot's pill, the pilot's header.
     *
     * <p>Same answer as {@link #longLabel} with the qualifiers dropped: a window is its title alone, the
     * desktop does not spell out that it means every monitor, and a region is only said to be one. One method
     * rather than each surface trimming the long one, so they cannot disagree.
     */
    public static String shortLabel(CaptureSource source) {
        CaptureSource whole = whole(source);
        String label;
        if (whole instanceof CurrentSource) label = "Project default";
        else if (whole instanceof Monitor monitor) label = "Screen " + (monitor.index() + 1);
        else if (whole instanceof NamedWindow window) {
            label = blank(window.titleSubstring()) ? "Window" : window.titleSubstring();
        } else if (whole instanceof EmulatorSource emulator) {
            label = blank(emulator.instanceName()) ? "Emulator" : emulator.instanceName();
        } else label = "Whole desktop";
        return region(source) == null ? label : label + " (region)";
    }

    /**
     * Whether {@code a} and {@code b} name the same thing to capture — same kind, same monitor, title or
     * instance, same region. The sources have no {@code equals} of their own; the source picker asks this to
     * pre-select the tile a slot already holds.
     */
    public static boolean same(CaptureSource a, CaptureSource b) {
        if (a == null || b == null) return false;
        CaptureSource wa = whole(a), wb = whole(b);
        return wa.getClass() == wb.getClass() && shortLabel(wa).equals(shortLabel(wb))
                && Objects.equals(region(a), region(b));
    }

    /**
     * {@code source} without its region narrowing, or {@code source} itself.
     *
     * <p>Unwrapped by hand rather than through {@link CaptureSource#base()}: {@code base()} of
     * {@code Source.current()} resolves whatever <em>this</em> process installed, and in the editor that is the
     * desktop, not the bot's source.
     */
    public static CaptureSource whole(CaptureSource source) {
        CaptureSource s = source;
        while (s instanceof RegionSource region) s = region.parent();
        return s;
    }

    /**
     * The rectangle {@code source} crops out of {@link #whole}, in that surface's own pixels, or {@code null}
     * when it reads all of it. A region of a region is the inner one moved by the outer's corner.
     */
    public static Rect region(CaptureSource source) {
        if (!(source instanceof RegionSource region) || region.sub() == null) return null;
        Rect sub = region.sub();
        Rect outer = region(region.parent());
        int x = Math.max(0, sub.x()) + (outer == null ? 0 : outer.x());
        int y = Math.max(0, sub.y()) + (outer == null ? 0 : outer.y());
        return new Rect(x, y, sub.width(), sub.height());
    }

    /**
     * Which screen {@code source} reads, or {@code 0}.
     *
     * <p>Zero for a source that is not a monitor at all, because both end in the same place: something has
     * to be captured, and the primary screen is the answer that always exists. A caller that needs to tell
     * the two apart asks {@link #isDesktop} first.
     */
    public static int monitorIndex(CaptureSource source) {
        return whole(source) instanceof Monitor monitor ? Math.max(0, monitor.index()) : 0;
    }

    /** The window title substring {@code source} matches on, or {@code null} when it reads no window. */
    public static String windowTitle(CaptureSource source) {
        return whole(source) instanceof NamedWindow window ? window.titleSubstring() : null;
    }

    /** The emulator instance {@code source} reads, or {@code null} when it reads no emulator. */
    public static String emulatorName(CaptureSource source) {
        return whole(source) instanceof EmulatorSource emulator ? emulator.instanceName() : null;
    }

    /** True for the whole virtual desktop — including {@code null}, which every reader treats as it. */
    public static boolean isDesktop(CaptureSource source) {
        CaptureSource whole = whole(source);
        return !(whole instanceof Monitor || whole instanceof NamedWindow || whole instanceof EmulatorSource);
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
