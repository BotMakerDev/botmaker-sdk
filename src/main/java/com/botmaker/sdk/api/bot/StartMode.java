package com.botmaker.sdk.api.bot;

/**
 * Why the supervisor is invoking the game start-up step, so the generated {@code Startup} can do the right
 * thing for each case rather than launching unconditionally every time.
 *
 * <ul>
 *   <li>{@link #COLD} — the one-time start before the first loop pass. The game may already be open (the user
 *       launched it by hand), so this should <em>not</em> force a relaunch of something already running.</li>
 *   <li>{@link #RESTART} — recovery after a crash/stuck. The game may be frozen, so this should shut it down
 *       first and bring it back up cleanly.</li>
 * </ul>
 *
 * @see Bot#start(Runnable, Runnable, java.util.function.Consumer)
 */
public enum StartMode {
    /** First launch, before the loop: bring the game up only if it isn't already running. */
    COLD,
    /** Recovery restart: shut the (possibly frozen) game down first, then bring it back up. */
    RESTART
}
