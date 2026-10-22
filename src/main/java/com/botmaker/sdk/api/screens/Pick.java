package com.botmaker.sdk.api.screens;

import com.botmaker.sdk.api.vision.ImageTemplate;

import java.util.Locale;

/**
 * Which copies of a {@link Button#repeated repeated button} are pressed, along an {@link Order} — {@code
 * Pick.each(Order.NORTH)}: the first, the nth, each in turn, or each until none is left (the user's call,
 * 2026-10-16, in place of one input per copy). Of a {@link Button#cycling cycling} button, {@link #until} presses
 * until it shows one of its looks (2026-10-17). A copy is also picked by where it sits from something else the
 * screen shows ({@link #at}), and a {@link Button#grid grid}'s cell by its row and column ({@link #cell}, 2026-10-22).
 *
 * @param mode   how many copies
 * @param nth    which copy for {@link Mode#NTH}, from 1, or which row for {@link Mode#CELL}; 0 otherwise
 * @param order  which copy comes first
 * @param look   the look {@link Mode#UNTIL} presses until; null otherwise
 * @param column which column for {@link Mode#CELL}, from 1; 0 otherwise
 * @param place  where the copy {@link Mode#AT} presses sits; null otherwise
 */
public record Pick(Mode mode, int nth, Order order, ImageTemplate look, int column, Place place) {

    /** How many copies a pick presses. */
    public enum Mode {
        /** The first copy along the order. */
        FIRST,
        /** The nth copy along the order. */
        NTH,
        /** Every copy shown, once each. */
        EACH,
        /** The first copy, again and again, until none is left or the button looks disabled. */
        UNTIL_NONE,
        /** The button, again and again, until it shows {@link #look}: at most once round its cycle. */
        UNTIL,
        /** The copy nearest the anchor of {@link #place} that meets it. */
        AT,
        /** A grid's cell, by its row ({@link #nth}) and column. */
        CELL
    }

    public Pick {
        mode = mode == null || mode == Mode.AT && place == null ? Mode.FIRST : mode;
        nth = mode == Mode.NTH || mode == Mode.CELL ? Math.max(1, nth) : 0;
        column = mode == Mode.CELL ? Math.max(1, column) : 0;
        order = order == null ? Order.NORTH : order;
        look = mode == Mode.UNTIL ? look : null;
        place = mode == Mode.AT ? place : null;
    }

    /** A pick of a look: every mode but {@link Mode#AT} and {@link Mode#CELL}. */
    public Pick(Mode mode, int nth, Order order, ImageTemplate look) {
        this(mode, nth, order, look, 0, null);
    }

    /** A pick of no look: every mode but {@link Mode#UNTIL}, {@link Mode#AT} and {@link Mode#CELL}. */
    public Pick(Mode mode, int nth, Order order) {
        this(mode, nth, order, null);
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

    /**
     * The topmost copy until none is left or it looks disabled: Buy until the money runs out. Of a repeated button,
     * a topmost copy greyed out stops it, though one further down is lit.
     */
    public static Pick untilNone() {
        return new Pick(Mode.UNTIL_NONE, 0, Order.NORTH);
    }

    /** Presses until the button shows {@code look}, none when it already does: the speed at x50. */
    public static Pick until(ImageTemplate look) {
        return new Pick(Mode.UNTIL, 0, Order.NORTH, look);
    }

    /**
     * The copy {@code place} says, nearest its anchor: {@code Pick.at(Place.below(Pictures.EVENT_HEADER))}. None
     * meets it, or the anchor is not shown: nothing is pressed.
     */
    public static Pick at(Place place) {
        return new Pick(Mode.AT, 0, Order.NORTH, null, 0, place);
    }

    /** A grid's cell at {@code row} and {@code column}, from 1, row 1 the top: {@code Pick.cell(2, 3)}. */
    public static Pick cell(int row, int column) {
        return new Pick(Mode.CELL, row, Order.NORTH, null, column, null);
    }

    /** How it reads in a sentence: {@code #2 from the north}, {@code each from the north}. */
    public String label() {
        String from = " from the " + order.name().toLowerCase(Locale.ROOT).replace('_', '-');
        return switch (mode) {
            case FIRST -> "first" + from;
            case NTH -> "#" + nth + from;
            case EACH -> "each" + from;
            case UNTIL_NONE -> "until none";
            case UNTIL -> "until it shows " + (look == null ? "?" : look.id());
            case AT -> "the one " + place.label();
            case CELL -> "row " + nth + ", column " + column;
        };
    }
}
