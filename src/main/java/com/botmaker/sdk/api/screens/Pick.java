package com.botmaker.sdk.api.screens;

import java.util.Locale;

/**
 * Which copies of a {@link Button#repeated repeated button} are pressed, along an {@link Order} — {@code
 * Pick.each(Order.NORTH)}: the first, the nth, each in turn, or each until none is left (the user's call,
 * 2026-10-16, in place of one input per copy).
 *
 * @param mode  how many copies
 * @param nth   which copy for {@link Mode#NTH}, from 1; 0 otherwise
 * @param order which copy comes first
 */
public record Pick(Mode mode, int nth, Order order) {

    /** How many copies a pick presses. */
    public enum Mode {
        /** The first copy along the order. */
        FIRST,
        /** The nth copy along the order. */
        NTH,
        /** Every copy shown, once each. */
        EACH,
        /** The first copy, again and again, until none is left or the button looks disabled. */
        UNTIL_NONE
    }

    public Pick {
        mode = mode == null ? Mode.FIRST : mode;
        nth = mode == Mode.NTH ? Math.max(1, nth) : 0;
        order = order == null ? Order.NORTH : order;
    }

    /** The topmost copy: what a way or a press takes when it names none. */
    public static Pick first() {
        return new Pick(Mode.FIRST, 0, Order.NORTH);
    }

    /** The first copy along {@code order}. */
    public static Pick first(Order order) {
        return new Pick(Mode.FIRST, 0, order);
    }

    /** The {@code n}th copy along {@code order}, from 1. */
    public static Pick nth(int n, Order order) {
        return new Pick(Mode.NTH, n, order);
    }

    /** Every copy along {@code order}, once each. */
    public static Pick each(Order order) {
        return new Pick(Mode.EACH, 0, order);
    }

    /** The topmost copy until none is left or it looks disabled: Buy until the money runs out. */
    public static Pick untilNone() {
        return new Pick(Mode.UNTIL_NONE, 0, Order.NORTH);
    }

    /** How it reads in a sentence: {@code #2 from the north}, {@code each from the north}. */
    public String label() {
        String from = " from the " + order.name().toLowerCase(Locale.ROOT).replace('_', '-');
        return switch (mode) {
            case FIRST -> "first" + from;
            case NTH -> "#" + nth + from;
            case EACH -> "each" + from;
            case UNTIL_NONE -> "until none";
        };
    }
}
