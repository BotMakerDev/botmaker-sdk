package com.botmaker.sdk.internal.trace;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The two things a debug trace needs that {@link com.botmaker.sdk.api.Debug} does not provide: a duration
 * rendered the way a person reads one, and a way to say "this happened 47 times" in one line instead of 47.
 *
 * <p><b>Why the collapsing exists.</b> A bot's interesting moments — an activity finishing, a popup dismissed,
 * a template located — happen a few times a minute, and printing them makes a run legible. Its uninteresting
 * ones happen a few hundred times a second: a wait loop polls, and each poll is a capture, a match and a miss.
 * Printing those unconditionally does not add detail to the log, it destroys the log, because the handful of
 * lines that mattered scroll past inside a wall of identical ones. So a repeated event is <em>counted</em> and
 * reported once per run, and the count is the information: {@code Foo not found ×47 in 3.4s} says both that
 * the bot was waiting and how hard it looked.
 *
 * <p>Deliberately in {@code internal}: this is the shape of the SDK's own diagnostics, not something a bot
 * writes against. A bot that wants to trace calls {@code Debug.log}.
 */
public final class Trace {

    private Trace() {}

    /**
     * {@code millis} as a duration a person reads at a glance — {@code 340ms} below a second, {@code 1.4s}
     * above one. Sub-second precision stops mattering exactly when the number gets large enough to notice.
     */
    public static String elapsed(long millis) {
        // ROOT, not the default locale: a trace is read next to code and grepped by people who wrote "1.4s",
        // and on a French desktop the default would silently render it "1,4s".
        return millis < 1_000 ? millis + "ms" : String.format(Locale.ROOT, "%.1fs", millis / 1_000.0);
    }

    /**
     * Open runs of a repeating event, keyed by whatever distinguishes one run from another (a template id, or
     * a single constant when there is only one kind).
     *
     * <p>A run is closed — and so reported — by one of two things: the event's <em>opposite</em> happening
     * ({@link #flush}, e.g. the template was finally found), or the run outliving {@link #REPORT_AFTER_MS}
     * ({@link #tick}). The second is what stops a bot that waits five minutes for something from being
     * completely silent about it for five minutes, which would read as a hang rather than as a wait.
     *
     * <p>Thread-safe by the crude method — every operation is synchronized — because bots run several threads
     * and the cost of a lock here is invisible next to the OpenCV match that precedes each call.
     */
    public static final class Runs {

        /** How long a run may accumulate before it reports itself, so a long wait is never silent. */
        private static final long REPORT_AFTER_MS = 5_000;

        /** A closed run: how many times it happened, and over how long. */
        public record Run(int count, long millis) {

            /** The run rendered as a suffix: {@code ×47 in 3.4s}. */
            @Override
            public String toString() {
                return "×" + count + " in " + elapsed(millis);
            }
        }

        private static final class Open {
            int count;
            long startedAt;
        }

        private final Map<String, Open> open = new HashMap<>();

        /**
         * Records one more occurrence for {@code key}, and returns the run it just closed if that run is old
         * enough to be worth a line — otherwise {@code null}, which is the common answer.
         */
        public synchronized Run tick(String key) {
            long now = System.currentTimeMillis();
            Open run = open.get(key);
            if (run == null) {
                run = new Open();
                run.startedAt = now;
                open.put(key, run);
            }
            run.count++;
            if (now - run.startedAt < REPORT_AFTER_MS) {
                return null;
            }
            open.remove(key);
            return new Run(run.count, now - run.startedAt);
        }

        /**
         * Closes {@code key}'s run because the thing it was counting stopped happening, and returns it — or
         * {@code null} when there was no run open, which is the case every time the event never repeated.
         */
        public synchronized Run flush(String key) {
            Open run = open.remove(key);
            return run == null ? null : new Run(run.count, System.currentTimeMillis() - run.startedAt);
        }
    }
}
