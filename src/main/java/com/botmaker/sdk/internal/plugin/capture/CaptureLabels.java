package com.botmaker.sdk.internal.plugin.capture;

import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.emulator.EmulatorSource;
import com.botmaker.sdk.internal.capture.Monitor;
import com.botmaker.sdk.internal.capture.NamedWindow;
import com.botmaker.shared.emulator.EmulatorInstances;

/**
 * What a {@link CaptureSource} is called on screen — a settings row, a menu entry, a toolbar button, the
 * line under a thumbnail.
 *
 * <h2>Here rather than on {@code CaptureSource}, and that is the same line as everywhere else</h2>
 *
 * <p>A capture source is what a <em>bot</em> reads pixels from; what to call one in a window is the
 * editor's business. Putting {@code longLabel()} on the interface would put three English sentences under
 * the SDK's never-delete contract and on every bot's classpath, for a question no bot asks.
 *
 * <p>It replaced {@code CaptureTargetModel}'s label half on 2026-09-22. That record was a
 * {@code (spec, label)} pair over shared's {@code CaptureSourceKind} grammar — {@code desktop},
 * {@code monitor:0}, {@code window:Diablo IV} — which existed because a project's capture source was
 * <em>stored as text</em>, in {@code capture.json} and in {@code botmaker-project.properties}. Both are
 * deleted: the source is the expression {@code Sdk.captureSource()} returns, so a third spelling of a type
 * the SDK already publishes had nothing left to keep in step with.
 *
 * <p><b>What went with it is the user's own label</b>, the {@code label} component a target carried. A
 * source is named by what it is now, because there is no file left to keep a nickname in and the bot's own
 * Java has nowhere to put one. Every label below is derived, so two surfaces cannot disagree about how a
 * source is named.
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
     * monitors)</em>. An emulator is captioned by {@link EmulatorInstances#captionFor}, because the same
     * instance name may be a phone or an emulator and only a live scan can say which.
     */
    public static String longLabel(CaptureSource source) {
        if (source instanceof Monitor monitor) return "Screen " + (monitor.index() + 1);
        if (source instanceof NamedWindow window) {
            return "Window: " + (blank(window.titleSubstring()) ? "(any)" : window.titleSubstring());
        }
        if (source instanceof EmulatorSource emulator) {
            return EmulatorInstances.captionFor(emulator.instanceName());
        }
        return "Whole desktop (all monitors)";
    }

    /**
     * The short label — a toolbar button, the in-block capture-source button, the pilot's header.
     *
     * <p>Same answer as {@link #longLabel} with the qualifiers dropped: a window is its title alone, and
     * the desktop does not spell out that it means every monitor. One method rather than each surface
     * trimming the long one, so the three of them cannot disagree.
     */
    public static String shortLabel(CaptureSource source) {
        if (source instanceof Monitor monitor) return "Screen " + (monitor.index() + 1);
        if (source instanceof NamedWindow window) {
            return blank(window.titleSubstring()) ? "Window" : window.titleSubstring();
        }
        if (source instanceof EmulatorSource emulator) {
            return blank(emulator.instanceName()) ? "Emulator" : emulator.instanceName();
        }
        return "Whole desktop";
    }

    /**
     * Which screen {@code source} names, or {@code 0}.
     *
     * <p>Zero for a source that is not a monitor at all, because both end in the same place: something has
     * to be captured, and the primary screen is the answer that always exists. A caller that needs to tell
     * the two apart asks {@code instanceof Monitor} first, as this does.
     */
    public static int monitorIndex(CaptureSource source) {
        return source instanceof Monitor monitor ? Math.max(0, monitor.index()) : 0;
    }

    /** The window title substring {@code source} matches on, or {@code null} when it names no window. */
    public static String windowTitle(CaptureSource source) {
        return source instanceof NamedWindow window ? window.titleSubstring() : null;
    }

    /** The emulator instance {@code source} names, or {@code null} when it names no emulator. */
    public static String emulatorName(CaptureSource source) {
        return source instanceof EmulatorSource emulator ? emulator.instanceName() : null;
    }

    /** True for the whole virtual desktop — including {@code null}, which every reader treats as it. */
    public static boolean isDesktop(CaptureSource source) {
        return !(source instanceof Monitor || source instanceof NamedWindow
                || source instanceof EmulatorSource);
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
