package com.botmaker.sdk.api.bot;

import com.botmaker.plugin.api.palette.Hidden;
import com.botmaker.sdk.api.console.Debug;
import com.botmaker.sdk.internal.bot.BotStuckException;
import com.botmaker.sdk.internal.screens.RunReach;
import com.botmaker.sdk.internal.screens.ScreenGraph;
import com.botmaker.sdk.internal.screens.ScreenMaps;
import com.botmaker.sdk.internal.screens.ScreenWalk;
import com.botmaker.sdk.internal.trace.Trace;
import com.botmaker.shared.Diag;

import java.awt.image.BufferedImage;
import java.util.Optional;

/**
 * The "dismiss whatever the game just interrupted us with" hook: a check the SDK runs <em>before every vision
 * step</em>, so a bot's own logic never has to ask "is a popup covering the screen right now?".
 *
 * <p>A game interrupts with daily rewards, mail, level-ups and ads. Every one of them hides the thing the next
 * {@code find} was looking for, so without a guard each activity has to open with its own defensive dismissal
 * code — and get it right. This runs one project-wide check instead, at the only place that can't be
 * forgotten: inside the finder/clicker/waiter themselves.
 *
 * <p><b>Popups are screens of the map</b> (2026-10-16): each screen of the bot's {@code Screens} whose setup is {@code .popup()}
 * says what tells it (its markers) and how it closes (its {@code back}: a close button, or the choice that closes it,
 * Offline progress' Claim). The check looks for every popup's markers on one frame and closes the one it finds by its
 * {@code back}, then waits for its markers to go; one still shown gets Escape once, and one that stays after that
 * throws {@link BotStuckException}, so the run's recovery takes over. A map with no popup costs nothing: no frame is
 * taken. {@code Navigate} closes popups the same way on its walks.
 *
 * <p><b>An overlay the map lacks.</b> When a wait times out on a frame that shows no screen of the map, {@link
 * #uncover()} tries the map's close buttons (a {@code Navigate} walk then Escape too) and warns that an unmapped popup covered the screen, so
 * the bot's author maps it in Upkeep.
 *
 * <p><b>Reentrancy.</b> The check's own looks go through the vision code that calls it. A thread-local flag makes
 * {@link #check()} a no-op while the check is already running on that thread.
 *
 * <p><b>Where it does not run.</b> {@code ImageClicker.click(MatchResult)} deliberately isn't guarded: it
 * clicks a coordinate you already located in an earlier frame, and dismissing a popup first would move the
 * screen out from under it. The same reasoning is why the guard runs <em>before</em> a find rather than after.
 *
 * <p>Process-global, like {@link BotSettings} — a bot has one screen and one guard. {@link #enabled(boolean)} has no
 * {@code enable()}/{@code disable()} pair to hide behind: without it the guard could not be switched off at all.
 */
public final class PopupGuard {

    private PopupGuard() {}

    /** The switch a body turns around a popup-shaped step, back on at each run's start; see {@link #enabled(boolean)}. */
    private static volatile boolean enabled = true;

    /** True while this thread is inside {@link #check()} or {@link #uncover()} — the recursion stop. */
    private static final ThreadLocal<Boolean> running = ThreadLocal.withInitial(() -> Boolean.FALSE);

    /** Where the warning for an unmapped popup is written from. */
    private static final String SOURCE = "Screens";

    /**
     * Turns the guard on or off until it is turned again. A body that is <em>itself</em> working through a
     * popup-shaped screen turns it off around that step, so the popup is not closed underneath it, and back on
     * after. Each run starts with it on.
     */
    public static void enabled(boolean on) {
        enabled = on;
    }

    /** Whether the guard is switched on and the bot's map has a popup it can tell. */
    public static boolean isEnabled() {
        return enabled && walk(ScreenMaps.installed()).hasPopups();
    }

    /**
     * Closes the popups of the bot's map the game shows now, unless the check is already running on this thread (see
     * <b>Reentrancy</b> above), the map has none, or the guard is switched off. The vision facades call this
     * themselves before every step that takes its own capture; a bot only calls it directly to force an extra check.
     *
     * @throws BotStuckException when a popup is still shown after its way out and Escape
     */
    public static void check() {
        if (!enabled || Boolean.TRUE.equals(running.get()) || Thread.currentThread().isInterrupted()) return;
        ScreenWalk walk = walk(ScreenMaps.installed());
        if (!walk.hasPopups()) return;
        running.set(Boolean.TRUE);
        long startedAt = System.currentTimeMillis();
        try {
            walk.closePopups(new RunReach().look());
        } catch (ScreenWalk.Stayed stayed) {
            throw new BotStuckException(stayed.getMessage());
        } finally {
            running.set(Boolean.FALSE);
            trace(System.currentTimeMillis() - startedAt);
        }
    }

    /**
     * After a wait timed out: when the game shows no screen of the bot's map, clears what covers it (the map's close
     * buttons; never Escape, which only {@code Navigate}'s walks press) and warns that an unmapped popup covered the screen it was last seen on. Nothing while the
     * guard is off or already running on this thread, or while the map tells no screen yet.
     */
    @Hidden("called by the waits when they time out; a bot never calls it")
    public static void uncover() {
        if (!enabled || Boolean.TRUE.equals(running.get()) || Thread.currentThread().isInterrupted()) return;
        ScreenWalk walk = walk(ScreenMaps.installed());
        running.set(Boolean.TRUE);
        try {
            Optional<BufferedImage> frame;
            try {
                frame = new RunReach().look();
            } catch (RuntimeException uncaptured) {
                return;    // the wait already failed; a source that will not capture uncovers nothing
            }
            String near = ScreenMaps.lastSeen();
            // No Escape here: a wait may time out on a screen the map lacks on purpose, where Escape could quit.
            frame.flatMap(f -> walk.uncover(f, near, false)).ifPresent(cleared -> {
                ScreenMaps.seen(cleared.at());
                Diag.warn(SOURCE, walk.covered(near, cleared));
            });
        } catch (ScreenWalk.Stayed stayed) {
            throw new BotStuckException(stayed.getMessage());
        } finally {
            running.set(Boolean.FALSE);
        }
    }

    private static ScreenWalk walk(ScreenGraph map) {
        return new ScreenWalk(map, new RunReach(), ScreenWalk.STEP_MS, null);
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
