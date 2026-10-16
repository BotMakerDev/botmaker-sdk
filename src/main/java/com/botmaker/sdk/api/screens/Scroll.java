package com.botmaker.sdk.api.screens;

import java.time.Duration;

/**
 * How a {@link Strip} is scrolled, and how it behaves once scrolled — {@code Scroll.wheel(-5).step(100)
 * .settle(Duration.ofMillis(800))}. The wheel is turned, or the pointer dragged, over a member that shows; a strip is
 * looked at again once it holds still, for up to its {@link #settle}, and is at its end when a scroll moved its
 * members less than a quarter of its {@link #step}.
 *
 * <p>The factories give a strip not measured yet the SDK's numbers ({@link #SETTLE}, {@link #MOST}, no step);
 * Discovery scrolls a strip once and writes what it measured: how far its members moved and how long they took to
 * hold still (2026-10-17). A strip's own numbers make a fast one quick to judge and a slow one judged only once it
 * stopped.
 *
 * @param notches how far each turn of the wheel goes: negative down or toward you, positive up or away; 0 for a drag
 * @param drag    which way the pointer is dragged, the way the members move; null for the wheel
 * @param px      how far the pointer is dragged; 0 for the wheel
 * @param time    how long a drag takes; zero for the wheel
 * @param step    how far the members move on one scroll, in the frame's pixels; 0 when not measured
 * @param settle  the longest the strip is looked at after a scroll for its members to hold still
 * @param most    the most scrolls each way looking for a member
 */
public record Scroll(int notches, Order drag, int px, Duration time, int step, Duration settle, int most) {

    /**
     * How long a strip not measured yet is looked at after a scroll: Firestone's column glides for over a second, and
     * judged before it stopped it looked at its end (2026-10-16).
     */
    public static final Duration SETTLE = Duration.ofMillis(3000);

    /** The most scrolls each way looking for a member, by default: a strip longer than that is a screen's own. */
    public static final int MOST = 8;

    public Scroll {
        if (drag == Order.CENTER) throw new IllegalArgumentException("A drag goes toward a side or a corner.");
        time = time == null ? Duration.ZERO : time;
        settle = settle == null || settle.isNegative() || settle.isZero() ? SETTLE : settle;
        step = Math.max(0, step);
        most = most <= 0 ? MOST : most;
    }

    /** The wheel turned {@code notches} over a member that shows. */
    public static Scroll wheel(int notches) {
        return new Scroll(notches, null, 0, Duration.ZERO, 0, SETTLE, MOST);
    }

    /** The pointer dragged {@code px} toward {@code way} over {@code time}, from a member that shows. */
    public static Scroll drag(Order way, int px, Duration time) {
        return new Scroll(0, way, px, time, 0, SETTLE, MOST);
    }

    /** This scroll, its members moving {@code px} on each. */
    public Scroll step(int px) {
        return new Scroll(notches, drag, this.px, time, px, settle, most);
    }

    /** This scroll, its strip looked at for up to {@code time} to hold still after each. */
    public Scroll settle(Duration time) {
        return new Scroll(notches, drag, px, this.time, step, time, most);
    }

    /** This scroll, done at most {@code n} times each way looking for a member. */
    public Scroll most(int n) {
        return new Scroll(notches, drag, px, time, step, settle, n);
    }

    /** Whether it scrolls at all: a wheel that turns, or a drag that goes somewhere. */
    public boolean moves() {
        return drag == null ? notches != 0 : px > 0;
    }

    /** This scroll the other way: the wheel turned back, or the drag the opposite way. */
    public Scroll back() {
        return drag == null ? new Scroll(-notches, null, px, time, step, settle, most)
                : new Scroll(notches, opposite(drag), px, time, step, settle, most);
    }

    /** How the scroll reads in a reply: {@code wheel -5} or {@code drag NORTH 300px}. */
    public String label() {
        return drag == null ? "wheel " + notches : "drag " + drag + " " + px + "px";
    }

    private static Order opposite(Order way) {
        return switch (way) {
            case NORTH -> Order.SOUTH;
            case SOUTH -> Order.NORTH;
            case EAST -> Order.WEST;
            case WEST -> Order.EAST;
            case NORTH_EAST -> Order.SOUTH_WEST;
            case SOUTH_WEST -> Order.NORTH_EAST;
            case NORTH_WEST -> Order.SOUTH_EAST;
            case SOUTH_EAST -> Order.NORTH_WEST;
            case CENTER -> Order.CENTER;
        };
    }
}
