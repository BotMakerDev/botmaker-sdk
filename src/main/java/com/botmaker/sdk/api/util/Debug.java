package com.botmaker.sdk.api.util;

import com.botmaker.plugin.api.palette.Hidden;
import com.botmaker.plugin.api.palette.Palette;
import com.botmaker.sdk.internal.config.ProjectDefaults;
import com.botmaker.shared.Diag;

/**
 * The single, global debug-output switch for a running bot. One flag governs <em>all</em> of the SDK's
 * diagnostic printing:
 * <ul>
 *   <li>the lifecycle/launch traces — {@code [Bot]}, {@code [Game]}, {@code [Target]}, {@code [Activity]}
 *       — that used to print unconditionally, and</li>
 *   <li>the vision traces (find/click/wait/pixel/text) that used to be gated by the separate
 *       {@code ClickConfig.DEBUG_MODE}; {@code BotSettings.enableDebugMode(...)} now delegates here.</li>
 * </ul>
 *
 * <p><b>Default: on.</b> A bot prints its trace out of the box so a first run is legible; turn it off for a
 * quiet production run with {@link #disable()} (or the Studio "Debug output" toggle). The initial state is
 * seeded from the project's {@code debug} key in {@code botmaker-project.properties} (see
 * {@link ProjectDefaults#debug()}) — absent/unparseable leaves it on — and can be overridden at runtime.
 *
 * <p>Emit your own trace through {@link #log(String)} / {@link #error(String)}: they print only when debugging
 * is enabled, so bot code never has to wrap prints in an {@code if}.
 *
 * <p>The flag itself lives in {@code botmaker-shared}'s {@link Diag}, which this class only delegates to.
 * {@code shared} can't depend on the SDK, yet its window/capture/input code prints diagnostics of its own —
 * keeping the state in the lower module is what makes this <em>one</em> switch rather than two that drift.
 *
 * <p><b>Curated for the palette</b> (see {@code @Palette}): six of the seven are offered. The one hidden is
 * {@link #set(boolean)}, and it is the third instance of a shape this sweep keeps finding — a method whose
 * {@code boolean} argument selects between two behaviours that <em>already have their own names</em>. It is
 * {@code Mouse.scroll(int)} again with a flag instead of a sign: {@link #enable()} and {@link #disable()} say
 * at the call site what {@code set(false)} makes the reader work out. Offering all three as equals asks the
 * user to choose a spelling for no gain. It stays public for the bot that computes the flag.
 *
 * <p>{@link #error(String, Throwable)} <em>is</em> offered, which is worth stating because {@code Throwable}
 * is a JDK type and this sweep has hidden JDK-typed parameters elsewhere ({@code ZoneId}, {@code OcrOptions}).
 * That rule was never about the package: it is about an argument the editor has <b>no way to produce</b>. A
 * {@code Throwable} is produced by the {@code catch} clause the call sits in, so the variable picker fills it
 * from scope — and this overload's own javadoc is the reason to prefer it over {@code t.printStackTrace()}.
 */
@Palette(category = "util", categoryLabel = "Utilities", icon = "🐞", order = 31)
@Hidden("kept out of the insert menus; its members stay catalogued so the name resolves")
public final class Debug {

    private Debug() {}

    static {
        // Seed the shared flag from the project's `debug` key; absent/unparseable leaves it on.
        Boolean configured = ProjectDefaults.debug();
        Diag.set(configured == null || configured);
    }

    /** Whether debug output is currently on. All SDK diagnostic prints consult this. */
    public static boolean isEnabled() {
        return Diag.isEnabled();
    }

    /** Turns debug output on for the rest of the run. */
    public static void enable() {
        Diag.set(true);
    }

    /** Turns debug output off for the rest of the run (a quiet production run). */
    public static void disable() {
        Diag.set(false);
    }

    /** Sets debug output on or off. */
    public static void set(boolean on) {
        Diag.set(on);
    }

    /** Prints {@code message} to stdout when debugging is on; a no-op when off. */
    public static void log(String message) {
        Diag.log(message);
    }

    /** Prints {@code message} to stderr when debugging is on; a no-op when off. */
    public static void error(String message) {
        Diag.error(message);
    }

    /**
     * Prints {@code message} then {@code t}'s stack trace to stderr, when debugging is on. Use this instead of
     * {@code t.printStackTrace()}, which would print on a quiet run.
     */
    public static void error(String message, Throwable t) {
        Diag.error(message, t);
    }
}
