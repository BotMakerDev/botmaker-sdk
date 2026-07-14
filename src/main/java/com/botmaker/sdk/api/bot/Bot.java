package com.botmaker.sdk.api.bot;

/**
 * Bot lifecycle supervisor: the outermost loop that keeps a bot running through crashes and stuck states.
 *
 * <p>{@link #supervise} runs your bot body forever, and whenever it throws — a
 * {@link BotStuckException} from the {@link Watchdog}, or any other {@link RuntimeException} — catches it,
 * resets the watchdog and runs your recovery (typically {@code goHome()} then {@code startGame()}) to get
 * back to a known-good state before restarting. This is the "restart the bot on failure" machinery a game
 * bot needs; the body, the recovery hooks and the per-activity logic stay in editable user code.
 */
public final class Bot {

    private Bot() {}

    /**
     * Run {@code body} forever, recovering with {@code recovery} whenever it throws. Enables the
     * {@link Watchdog} so stuck states surface as {@link BotStuckException}. Does not return under normal
     * operation.
     *
     * @param body     the bot's main work (e.g. one pass of the macro loop; it is re-run continuously)
     * @param recovery run after a crash/stuck to restore a known-good state before the next attempt
     */
    public static void supervise(Runnable body, Runnable recovery) {
        Watchdog.enable();
        while (true) {
            try {
                body.run();
            } catch (BotStuckException e) {
                System.err.println("[Bot] Stuck: " + e.getMessage() + " — recovering.");
                Watchdog.reset();
                recovery.run();
            } catch (RuntimeException e) {
                System.err.println("[Bot] Crashed: " + e + " — recovering.");
                Watchdog.reset();
                recovery.run();
            }
        }
    }

    /**
     * Convenience supervisor whose recovery is "get back home, then (re)start the game". Equivalent to
     * {@link #supervise(Runnable, Runnable)} with a recovery that runs {@code goHome} then {@code startGame}.
     *
     * @param body      the bot's main work
     * @param goHome    navigate from wherever the bot is back to a safe/home screen
     * @param startGame (re)launch or restart the game from the home screen
     */
    public static void supervise(Runnable body, Runnable goHome, Runnable startGame) {
        supervise(body, () -> {
            goHome.run();
            startGame.run();
        });
    }
}
