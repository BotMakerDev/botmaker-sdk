package com.botmaker.sdk.api.flow.activities;

import com.botmaker.sdk.api.bot.Activity;

/**
 * A test activity, sitting where a generated one sits: {@code <the anchor's package>.activities.<Name>}.
 *
 * <p>The convention is the whole of what replaced the generated {@code ActivityRegistry}, so the test for it
 * has to exercise the convention itself rather than a stub handed in by hand.
 */
public class Mining extends Activity<Mining.Outcome> {

    public enum Outcome { NEXT, BAG_FULL }

    /** What {@link #run()} will report next; the test sets it to steer the walk. */
    public static Outcome next = Outcome.NEXT;

    /** How many times this activity has run, so a test can tell a loop from a single pass. */
    public static int runs;

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public Outcome run() {
        runs++;
        return next;
    }
}
