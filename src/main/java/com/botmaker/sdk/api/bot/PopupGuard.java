package com.botmaker.sdk.api.bot;

import com.botmaker.sdk.api.console.Debug;
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
 * which is exactly what {@link com.botmaker.sdk.api.vision.Matches} answers. The bot writes its own method and
 * names it in {@code Sdk.run()}, which {@code Bot.runFlow} installs here (2026-10-14); this class only decides
 * <em>when</em> it runs: before every vision call of every activity.
 *
 * <pre>{@code
 * Run.of().home(Gamebot::goHome).popups(Gamebot::clearPopups)     // Sdk.run()
 *
 * public static void clearPopups() {
 *     ImageFinder.whileFindAny(ImageTemplateGroup.of(Pictures.MAIL_CLOSE, Pictures.OFFER_CLOSE),
 *             found -> ImageClicker.clickLast());
 * }
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
 * <p>Process-global, like {@link BotSettings} — a bot has one screen and one guard.
 *
 * <p><b>Curated for the palette</b> (see {@code @Palette}): three of the five are offered — {@link #check()},
 * {@link #enabled(boolean)} and {@link #isEnabled()}, which are the vocabulary of a bot <em>using</em> the
 * guard. {@link #install(Runnable)} and {@link #uninstall()} are not: they are the wiring, done by
 * {@code Bot.runFlow} from {@code Run.popups}, and a second {@code install} inserted from a menu silently replaces
 * the bot's own popups method — a second call replaces the first. Same reasoning as {@code Activity.execute()}: a member the generated code
 * owns is not a menu entry, because reaching it by hand can only undo what the scaffold set up. Both stay
 * public for a bot that genuinely wants a custom guard.
 *
 * <p><b>{@link #enabled(boolean)} is offered, and that is deliberate rather than an oversight.</b> A
 * {@code boolean} argument selecting between two behaviours is exactly what this sweep hides in
 * {@code Debug.set}, {@code Activity.setEnabled} and {@code Mouse.scroll} — but in every one of those the two
 * values <em>already have their own named methods</em>, and the hiding is about the duplication, not about the
 * boolean. Here there is no {@code enable()}/{@code disable()} pair, so hiding this would leave the guard with
 * no way to be switched off at all. The rule applies where the alternative exists and is suspended where it
 * does not — the same discipline {@code Time}'s two surviving {@code *Utc} methods record.
 */
public final class PopupGuard {

    private PopupGuard() {}

    /** The project's check, or null when none is installed (then everything here is a no-op). */
    private static volatile Runnable handler;

    /** The switch a body turns around a popup-shaped step, back on at each run's start; see {@link #enabled(boolean)}. */
    private static volatile boolean enabled = true;

    /** True while this thread is inside {@link #check()} — the recursion stop. */
    private static final ThreadLocal<Boolean> running = ThreadLocal.withInitial(() -> Boolean.FALSE);

    /**
     * Installs the bot's popup check. {@code Bot.runFlow} calls it with the run's {@code Run.popups} method; a second
     * call replaces the first, and null removes it.
     */
    public static void install(Runnable check) {
        handler = check;
    }

    /** Removes the installed check — every {@link #check()} becomes a no-op again. */
    public static void uninstall() {
        handler = null;
    }

    /**
     * Turns the guard on or off until it is turned again. A body that is <em>itself</em> working through a
     * popup-shaped screen turns it off around that step, so the popup is not dismissed underneath it, and back on
     * after. The flow no longer switches it per activity (2026-10-14): popups are handled everywhere.
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
            Debug.log("check took " + Trace.elapsed(millis));
            return;
        }
        report(CHECKS.tick(RUN_KEY));
    }

    private static void report(Trace.Runs.Run run) {
        if (run != null) {
            Trace.log("checked", run);
        }
    }
}
