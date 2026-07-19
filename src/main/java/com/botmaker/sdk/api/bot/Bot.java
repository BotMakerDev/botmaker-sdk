package com.botmaker.sdk.api.bot;
import com.botmaker.sdk.api.Debug;

/**
 * Bot lifecycle supervisor: the outermost loop that keeps a bot running through crashes and stuck states.
 *
 * <p>{@link #supervise} runs your bot body forever, and whenever it throws — a
 * {@link BotStuckException} from the {@link Watchdog}, or any other {@link RuntimeException} — catches it,
 * resets the watchdog and runs your recovery (typically {@code goHome()} then {@code startGame()}) to get
 * back to a known-good state before restarting. This is the "restart the bot on failure" machinery a game
 * bot needs; the body, the recovery hooks and the per-activity logic stay in editable user code.
 *
 * <p>The 3-arg {@link #supervise(Runnable, Runnable, Runnable)} also runs your start-up sequence <em>once</em>
 * before the first loop pass — {@code startGame()} then {@code goHome()} — so a fresh launch actually opens
 * the game and reaches a known screen instead of assuming it is already running.
 *
 * <p>The bot ends when {@link #stop()} is called — from an activity that is done, or automatically by the
 * generated loop once every activity is disabled. {@code stop()} unwinds the supervise loop cleanly and
 * {@code supervise} returns, rather than treating it as a crash to recover from.
 */
public final class Bot {

    private Bot() {}

    /**
     * Signals a clean end of the bot. Thrown by {@link #stop()} and caught by {@link #supervise} to break the
     * loop. Private so the only public way to end the bot is {@code Bot.stop()} — users never see or throw it.
     */
    private static final class BotStoppedException extends RuntimeException {}

    /**
     * Ends the bot: unwinds the supervise loop cleanly from wherever it is called — e.g. an activity that has
     * finished its work, or a helper deep in the call stack. {@link #supervise} catches this and returns
     * instead of recovering. This is the deliberate "we're done" exit, as opposed to a crash.
     */
    public static void stop() {
        throw new BotStoppedException();
    }

    /**
     * Starts the bot: the single public entry point. Runs {@code body} forever, recovering with
     * {@code recovery} whenever it throws. Delegates to the internal supervise loop.
     *
     * @param body     the bot's main work (e.g. one pass of the macro loop; it is re-run continuously)
     * @param recovery run after a crash/stuck to restore a known-good state before the next attempt
     */
    public static void start(Runnable body, Runnable recovery) {
        supervise(body, recovery);
    }

    /**
     * Starts the bot with the "get home, then (re)start the game" recovery and a one-time cold start
     * before the loop — the shape a generated game bot uses. The single public entry point; delegates to
     * the internal supervise loop.
     *
     * @param body      the bot's main work
     * @param goHome    navigate from wherever the bot is back to a safe/home screen
     * @param startGame (re)launch or restart the game from the home screen
     */
    public static void start(Runnable body, Runnable goHome, Runnable startGame) {
        supervise(body, goHome, startGame);
    }

    /**
     * Run {@code body} forever, recovering with {@code recovery} whenever it throws. Enables the
     * {@link Watchdog} so stuck states surface as {@link BotStuckException}. Does not return under normal
     * operation.
     *
     * <p>Package-private: bots call {@link #start} — {@code supervise} is the internal loop, not part of
     * the public palette (Studio only surfaces {@code public} facade methods as blocks).
     *
     * @param body     the bot's main work (e.g. one pass of the macro loop; it is re-run continuously)
     * @param recovery run after a crash/stuck to restore a known-good state before the next attempt
     */
    static void supervise(Runnable body, Runnable recovery) {
        Watchdog.enable();
        while (true) {
            try {
                body.run();
            } catch (BotStoppedException e) {
                Debug.log("[Bot] Stopped by request.");
                return;
            } catch (BotStuckException e) {
                Debug.error("[Bot] Stuck: " + e.getMessage() + " — recovering.");
                Watchdog.reset();
                recovery.run();
            } catch (RuntimeException e) {
                Debug.error("[Bot] Crashed: " + e + " — recovering.");
                Watchdog.reset();
                recovery.run();
            }
        }
    }

    /**
     * Convenience supervisor whose recovery is "get back home, then (re)start the game", and which also runs a
     * one-time start-up before the loop.
     *
     * <p><b>Cold start (once, before the first pass):</b> {@code startGame} then {@code goHome} — launch the
     * game, then navigate to a known-good screen. Without this the loop began against whatever was on screen,
     * so "launch the game in Startup" never fired on a normal run (Startup only ran during recovery).
     *
     * <p><b>Recovery (on every crash/stuck):</b> {@code goHome} then {@code startGame} — get back home, then
     * (re)start the game. A failure <em>during</em> cold start routes through this same recovery rather than
     * aborting the bot.
     *
     * @param body      the bot's main work
     * @param goHome    navigate from wherever the bot is back to a safe/home screen
     * @param startGame (re)launch or restart the game from the home screen
     */
    static void supervise(Runnable body, Runnable goHome, Runnable startGame) {
        Runnable recovery = () -> {
            goHome.run();
            startGame.run();
        };
        Watchdog.enable();
        // Cold start: open the game and reach a known screen once, before the loop. A failure here recovers
        // exactly as a mid-run failure would, so a bad first launch still self-heals instead of exiting.
        try {
            startGame.run();
            goHome.run();
        } catch (BotStoppedException e) {
            Debug.log("[Bot] Stopped by request during start-up.");
            return;
        } catch (BotStuckException e) {
            Debug.error("[Bot] Stuck during start-up: " + e.getMessage() + " — recovering.");
            Watchdog.reset();
            recovery.run();
        } catch (RuntimeException e) {
            Debug.error("[Bot] Crashed during start-up: " + e + " — recovering.");
            Watchdog.reset();
            recovery.run();
        }
        supervise(body, recovery);
    }
}
