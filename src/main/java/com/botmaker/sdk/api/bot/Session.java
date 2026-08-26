package com.botmaker.sdk.api.bot;

import com.botmaker.plugin.api.palette.Facade;
import com.botmaker.sdk.internal.session.SessionBootstrap;

/**
 * Whether this bot runs in its own <b>private display</b>, and on which backend — the isolation switch, shaped
 * like {@link Debug}: a static facade a generated bot calls at the top of {@code main}.
 *
 * <p><b>What isolation buys.</b> An isolated bot brings up a nested {@code :N} X display, launches its target
 * into it and drives that display alone. The game's window is never on your desktop, so the bot doesn't steal
 * focus, doesn't fight you for the cursor, and your own clicks can't land in its window — you can keep using the
 * machine while it runs. A non-isolated bot shares the real {@code :0} with you and has none of those
 * properties.
 *
 * <p><b>Default: on.</b> Isolation is the intended way to run, so it needs no code at all; only opting out does —
 * and a project that opts out in Studio does it through the {@code session.isolated} key below rather than a
 * generated call, so a default project's source stays free of session boilerplate either way.
 *
 * <p><b>Precedence</b>, highest first — an explicit call always wins so a bot can force its own behaviour on a
 * machine whose environment says otherwise:
 * <ol>
 *   <li>an explicit {@link #enable()} / {@link #disable()} / {@link #set(boolean)} call in bot code;</li>
 *   <li>the {@code botmaker.session.isolated} system property;</li>
 *   <li>the {@code BOTMAKER_SESSION_ISOLATED} environment variable;</li>
 *   <li>the project's {@code session.isolated} key in {@code botmaker-project.properties};</li>
 *   <li>{@code true}.</li>
 * </ol>
 * {@link #useBackend(String)} follows the same ladder against {@code botmaker.session.backend} /
 * {@code session.backend}, with one difference: its bottom rung is not a fixed value but the
 * <em>kind-driven</em> choice — a game gets gamescope (a real GPU in the private display), a plain command gets
 * the lighter Xephyr. That auto-selection is almost always what you want; pin a backend only to reproduce a
 * problem.
 *
 * <p><b>Call before the first {@code Target.start()}.</b> The session is brought up by the first launch and then
 * reused for the rest of the run, so a call made after that point changes nothing — it does not tear down a
 * live session.
 *
 * <p>Linux-only in substance: on Windows there is no nested-display backend, bring-up declines and the bot runs
 * on the normal desktop. Calling these methods there is harmless.
 *
 * <p><b>Curated for the palette</b> (see {@code @Palette}): four of the eight are offered, and the split here
 * is unusually clean because this class already says which half is which in its own javadoc. The vocabulary —
 * {@link #isEnabled()}, {@link #enable()}, {@link #disable()}, {@link #useBackend(String)} — is offered.
 * {@link #set(boolean)} is not: it is {@code Debug.set(boolean)} exactly, a flag whose two values already have
 * named methods, and {@code Session.disable()} says at the call site what {@code set(false)} makes the reader
 * work out. {@link #pinnedBackend()} and {@link #override()} describe themselves as <em>internal plumbing</em>
 * and are the readers behind {@code SessionBootstrap}'s ladder rather than anything a bot asks; {@code override()}
 * additionally returns a tri-state {@code Boolean} whose {@code null} is the interesting value, which is not a
 * thing the editor can hold. {@link #clearOverrides()} exists for tests, by its own admission.
 *
 * <p>{@code useBackend} is offered even though its argument is a bare {@code String}, and that is the
 * fillable-argument rule rather than an exception to it: the accepted set is closed and named right here
 * ({@code "gamescope"}, {@code "xephyr"}, {@code "auto"}), and an unrecognised name degrades to {@code auto}
 * instead of throwing, so a menu entry cannot produce a bot that breaks.
 */
@Facade(category = "bot", categoryLabel = "Bot", role = "HIDDEN", order = 32)
public final class Session {

    private Session() {}

    /**
     * The explicit bot-code override, or {@code null} when bot code hasn't spoken — the distinction is the whole
     * point of not seeding this from the project file the way {@link Debug} does its flag. Seeding would make
     * "the project says true" indistinguishable from "the bot said true", and the override could then never be
     * ranked above the environment.
     */
    private static volatile Boolean isolatedOverride;

    /** The explicit backend id from bot code, or {@code null} for "not pinned" (includes an {@code auto} call). */
    private static volatile String backendOverride;

    /**
     * Whether this bot will run on a private display — the resolved answer across the whole precedence ladder
     * above, not merely what bot code asked for.
     */
    public static boolean isEnabled() {
        return SessionBootstrap.isolationRequested();
    }

    /** Runs this bot on a private display (the default; call it only to override an environment that says no). */
    public static void enable() {
        set(true);
    }

    /**
     * Runs this bot on the real desktop instead of a private display. The bot then shares your cursor and focus,
     * which is what you want while watching it work — and what you don't want while using the machine.
     */
    public static void disable() {
        set(false);
    }

    /** Turns isolation on or off, outranking the system property, environment and project setting. */
    public static void set(boolean isolated) {
        isolatedOverride = isolated;
    }

    /**
     * Pins the private display's backend: {@code "gamescope"} (embedded Xwayland on the real GPU — for Proton /
     * DXVK / any 3D target), {@code "xephyr"} (cheap software-rendered 2D), or {@code "auto"} / {@code null} to
     * restore the kind-driven choice. An unrecognised name is treated as {@code auto} rather than throwing, so a
     * project written by a newer Studio still runs.
     *
     * <p>Pinning Xephyr for a game is the one combination worth warning about: its software GL is what makes
     * store launchers and Proton titles abort, which is why nothing auto-selects it for them.
     */
    public static void useBackend(String backend) {
        backendOverride = backend == null || backend.isBlank() ? null : backend.trim();
    }

    /** The backend bot code pinned, or {@code null} when it left the choice automatic. Internal plumbing. */
    public static String pinnedBackend() {
        return backendOverride;
    }

    /** The isolation override bot code set, or {@code null} when it never called. Internal plumbing. */
    public static Boolean override() {
        return isolatedOverride;
    }

    /**
     * Forgets both overrides, returning the resolution to the property/environment/project ladder. Exists for
     * tests: these are process-wide statics that outrank every other rung, so one test's pin would otherwise
     * follow every later test in the same JVM.
     */
    public static void clearOverrides() {
        isolatedOverride = null;
        backendOverride = null;
    }
}
