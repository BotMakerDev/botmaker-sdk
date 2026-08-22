package com.botmaker.sdk.api.bot;

import com.botmaker.sdk.api.ApiId;
import com.botmaker.sdk.api.Debug;
import com.botmaker.sdk.internal.trace.Trace;

/**
 * The "dismiss whatever the game just interrupted us with" hook: a check the SDK runs <em>before every vision
 * step</em>, so a bot's own logic never has to ask "is a popup covering the screen right now?".
 *
 * <p>A game interrupts with daily rewards, mail, level-ups and ads. Every one of them hides the thing the next
 * {@code find} was looking for, so without a guard each activity has to open with its own defensive dismissal
 * code — and get it right. This runs one project-wide check instead, at the only place that can't be
 * forgotten: inside the finder/clicker/waiter themselves.
 *
 * <p><b>The logic is the bot author's, not ours.</b> Blind-clicking anything that looks like a ✕ is wrong: the
 * same cross may be part of the screen the bot is actually working on, and a popup's body usually isn't
 * clickable at all — so dismissal is a question about which <em>combination</em> of templates is on screen,
 * which is exactly what {@link com.botmaker.sdk.api.vision.Matches} answers. Studio therefore generates an
 * editable {@code Popups} activity in the project and installs it here; this class only decides <em>when</em>
 * it runs.
 *
 * <pre>{@code
 * PopupGuard.install(Popups.INSTANCE::execute);      // generated entry point
 *
 * // …and in the project's own Popups.run(), which the user edits in the block editor:
 * ImageFinder.whileFindAny(POPUPS, found -> {
 *     if (found.has(mail) && found.has(claimAll)) ImageClicker.click(found.get(claimAll));
 *     else if (found.has(tapToClose))             ImageClicker.click(found.get(tapToClose));
 * });
 * }</pre>
 *
 * <p><b>Reentrancy.</b> The check is itself written with {@code ImageFinder}/{@code ImageClicker}, which would
 * re-enter the guard and recurse forever. A thread-local flag makes {@link #check()} a no-op while the check is
 * already running on that thread — so the handler's own vision calls behave like ordinary ones.
 *
 * <p><b>Where it does not run.</b> {@code ImageClicker.click(MatchResult)} deliberately isn't guarded: it
 * clicks a coordinate you already located in an earlier frame, and dismissing a popup first would move the
 * screen out from under it. The same reasoning is why the guard runs <em>before</em> a find rather than after.
 *
 * <p>Process-global, like {@link com.botmaker.sdk.api.BotSettings} — a bot has one screen and one guard.
 */
@ApiId("popup-guard")
public final class PopupGuard {

    private PopupGuard() {}

    /** The project's check, or null when none is installed (then everything here is a no-op). */
    private static volatile Runnable handler;

    /** The per-activity toggle the generated flow driver writes; see {@link #enabled(boolean)}. */
    private static volatile boolean enabled = true;

    /** True while this thread is inside {@link #check()} — the recursion stop. */
    private static final ThreadLocal<Boolean> running = ThreadLocal.withInitial(() -> Boolean.FALSE);

    /**
     * Installs the project's popup check. Called once from the generated entry point with the project's
     * {@code Popups} activity; a second call replaces the first.
     */
    public static void install(Runnable check) {
        handler = check;
    }

    /** Removes the installed check — every {@link #check()} becomes a no-op again. */
    public static void uninstall() {
        handler = null;
    }

    /**
     * Turns the guard on or off for the rest of the run. This is the per-activity opt-out: the generated
     * {@code FlowDriver} emits one of these before each activity, from its "check for popups" tick, because an
     * activity that is <em>itself</em> working through a popup-shaped screen must not have it dismissed
     * underneath it.
     */
    public static void enabled(boolean on) {
        enabled = on;
    }

    /** Whether a check is installed and currently switched on. */
    public static boolean isEnabled() {
        return enabled && handler != null;
    }

    /**
     * Runs the installed check now, unless one is already running on this thread (see <b>Reentrancy</b> above),
     * no check is installed, or the guard is switched off. The vision facades call this themselves before every
     * step that takes its own capture; a bot only calls it directly to force an extra check.
     *
     * <p>Exceptions propagate: a check that throws {@link BotStuckException} must reach the supervisor, and one
     * that throws anything else is a bug worth surfacing rather than a silent no-op before every find.
     */
    public static void check() {
        Runnable current = handler;
        if (current == null || !enabled || Boolean.TRUE.equals(running.get())) return;
        running.set(Boolean.TRUE);
        long startedAt = System.currentTimeMillis();
        try {
            current.run();
        } finally {
            running.set(Boolean.FALSE);
            trace(System.currentTimeMillis() - startedAt);
        }
    }

    // --- Debug trace ---
    //
    // A check that takes longer than a screenshot is the thing worth seeing: the guard runs before *every*
    // vision step, so a slow one turns a bot that is working into a bot that looks hung, and the console's
    // account of that stall is otherwise a gap between two unrelated lines. That one is printed as it
    // happens.
    //
    // The other few hundred checks a second are not worth a line each — printing "checking" on entry and its
    // duration on exit would put two lines around every find in the log, which is the same drowning the
    // vision misses are collapsed to avoid. They are counted and reported as a run instead, so the trace
    // still says the guard is alive and roughly what it costs.

    /** Past this, one check is an event rather than overhead, and gets its own line. */
    private static final long SLOW_CHECK_MS = 500;

    /** The single run — there is one guard, so nothing distinguishes one check from another. */
    private static final String RUN_KEY = "check";

    private static final Trace.Runs CHECKS = new Trace.Runs();

    private static void trace(long millis) {
        if (!Debug.isEnabled()) return;
        if (millis >= SLOW_CHECK_MS) {
            // Close the quiet run first, so the slow line lands after the checks that preceded it, not
            // before them.
            report(CHECKS.flush(RUN_KEY));
            Debug.log("[Popup] check took " + Trace.elapsed(millis));
            return;
        }
        report(CHECKS.tick(RUN_KEY));
    }

    private static void report(Trace.Runs.Run run) {
        if (run != null) {
            Debug.log("[Popup] checked " + run);
        }
    }
}
