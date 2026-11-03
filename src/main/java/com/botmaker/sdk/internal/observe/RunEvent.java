package com.botmaker.sdk.internal.observe;

/**
 * What the SDK tells its own window about a running bot, in its own words: where it matched, clicked or swiped, and
 * which activity and screen it is in. Crosses the run channel as a frame ({@link RunFrames}) the host relays whole
 * to the SDK's plugin half, which alone reads it (spec {@code docs/superpowers/specs/2026-11-03-run-reports-design.md}
 * §7). Lines and questions are not events: they are the host's, through the channel's own members.
 */
public sealed interface RunEvent
        permits RunEvent.Match, RunEvent.Click, RunEvent.Region, RunEvent.Swipe, RunEvent.Step {

    /** The whole screen: an event about no window. */
    Target NO_SURFACE = new Target(null, 0, 0, 0, 0);

    /** The window an event happened in, in desktop pixels; a null title is the whole screen. */
    record Target(String title, int x, int y, int width, int height) {}

    /** A part of the target, in its pixels. */
    record Rect(int x, int y, int width, int height) {}

    /** Where the event happened. */
    Target target();

    /** The 1-based line of the bot's source that led to it, or {@code -1}. */
    int line();

    /** A picture looked for in {@code region}: found at {@code rect} with {@code confidence}, or not. */
    record Match(Target target, Rect region, Rect rect, double confidence, boolean found, int line)
            implements RunEvent {

        /** A match with no bot line. */
        public Match(Target target, Rect region, Rect rect, double confidence, boolean found) {
            this(target, region, rect, confidence, found, -1);
        }
    }

    /** A click at a point. */
    record Click(Target target, int x, int y, int button, int line) implements RunEvent {

        /** A click with no bot line. */
        public Click(Target target, int x, int y, int button) {
            this(target, x, y, button, -1);
        }
    }

    /** A part of the screen shown. */
    record Region(Target target, Rect rect, int line) implements RunEvent {

        /** A region with no bot line. */
        public Region(Target target, Rect rect) {
            this(target, rect, -1);
        }
    }

    /** A swipe from one point to another. */
    record Swipe(Target target, int x1, int y1, int x2, int y2, long durationMs, int line) implements RunEvent {

        /** A swipe with no bot line. */
        public Swipe(Target target, int x1, int y1, int x2, int y2, long durationMs) {
            this(target, x1, y1, x2, y2, durationMs, -1);
        }
    }

    /** The activity the flow is in, and the screen a walk reached as its action; drawn nowhere on the screen. */
    record Step(String activity, String action, int line) implements RunEvent {

        public Step {
            activity = activity == null ? "" : activity;
            action = action == null ? "" : action;
        }

        @Override
        public Target target() {
            return NO_SURFACE;
        }
    }
}
