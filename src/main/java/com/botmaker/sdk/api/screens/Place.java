package com.botmaker.sdk.api.screens;

import com.botmaker.sdk.api.vision.ImageTemplate;

import java.util.Locale;

/**
 * Where a copy of a button sits from something else the screen shows, a picture or {@link Caption words} — {@code
 * Place.rightOf(Caption.of(Texts.DAILY, Zones.QUESTS))}: the Claim on the row whose title reads Daily (the user's call,
 * 2026-10-22). A button {@link Button#place placed} on the map is only ever found there; a body picks a copy by one
 * with {@link Pick#at}. The copy nearest the anchor that meets the place is taken; none meets it, or the anchor is not
 * shown, and nothing is pressed.
 *
 * <ul>
 *   <li>{@link Side#ABOVE above} and {@link Side#BELOW below}: in the anchor's column, its centre no further across
 *   than the anchor's width from the anchor's centre, and past the anchor's edge;</li>
 *   <li>{@link Side#LEFT_OF left of} and {@link Side#RIGHT_OF right of}: in its row, likewise by height;</li>
 *   <li>{@link Side#NEAREST nearest}: any copy, the nearest first;</li>
 *   <li>{@link Side#BETWEEN between}: its centre inside the box spanning both anchors.</li>
 * </ul>
 *
 * @param side         where from the anchor
 * @param picture      the anchor, a picture; null when it is words
 * @param words        the anchor, words read in a zone; null when it is a picture
 * @param otherPicture the second anchor of {@link Side#BETWEEN}, a picture; null otherwise
 * @param otherWords   the second anchor of {@link Side#BETWEEN}, words; null otherwise
 */
public record Place(Side side, ImageTemplate picture, Caption words, ImageTemplate otherPicture, Caption otherWords) {

    /** Where a copy sits from the anchor. */
    public enum Side {
        ABOVE("above", "Above"),
        BELOW("below", "Below"),
        LEFT_OF("left_of", "Left of"),
        RIGHT_OF("right_of", "Right of"),
        NEAREST("nearest", "Nearest"),
        BETWEEN("between", "Between"),
        UNKNOWN("unknown", "?");

        private final String id;
        private final String displayName;

        Side(String id, String displayName) {
            this.id = id;
            this.displayName = displayName;
        }

        public String id() {
            return id;
        }

        public String displayName() {
            return displayName;
        }

        /** The side of id {@code id}, any case; {@link #UNKNOWN} for none. */
        public static Side fromId(String id) {
            if (id == null) return UNKNOWN;
            String wanted = id.strip().toLowerCase(Locale.ROOT).replace(' ', '_');
            for (Side side : values()) if (side.id.equals(wanted)) return side;
            return UNKNOWN;
        }
    }

    public Place {
        side = side == null ? Side.UNKNOWN : side;
        // One anchor, a picture or words; a second only between two.
        if (picture != null) words = null;
        if (side != Side.BETWEEN) {
            otherPicture = null;
            otherWords = null;
        } else if (otherPicture != null) {
            otherWords = null;
        }
    }

    /** The copy above {@code anchor}, in its column. */
    public static Place above(ImageTemplate anchor) {
        return new Place(Side.ABOVE, anchor, null, null, null);
    }

    /** The copy above {@code words}, in their column. */
    public static Place above(Caption words) {
        return new Place(Side.ABOVE, null, words, null, null);
    }

    /** The copy below {@code anchor}, in its column. */
    public static Place below(ImageTemplate anchor) {
        return new Place(Side.BELOW, anchor, null, null, null);
    }

    /** The copy below {@code words}, in their column. */
    public static Place below(Caption words) {
        return new Place(Side.BELOW, null, words, null, null);
    }

    /** The copy left of {@code anchor}, in its row. */
    public static Place leftOf(ImageTemplate anchor) {
        return new Place(Side.LEFT_OF, anchor, null, null, null);
    }

    /** The copy left of {@code words}, in their row. */
    public static Place leftOf(Caption words) {
        return new Place(Side.LEFT_OF, null, words, null, null);
    }

    /** The copy right of {@code anchor}, in its row: the Claim on a row whose title is a picture. */
    public static Place rightOf(ImageTemplate anchor) {
        return new Place(Side.RIGHT_OF, anchor, null, null, null);
    }

    /** The copy right of {@code words}, in their row: the Claim on the row titled so. */
    public static Place rightOf(Caption words) {
        return new Place(Side.RIGHT_OF, null, words, null, null);
    }

    /** The copy nearest {@code anchor}. */
    public static Place nearest(ImageTemplate anchor) {
        return new Place(Side.NEAREST, anchor, null, null, null);
    }

    /** The copy nearest {@code words}. */
    public static Place nearest(Caption words) {
        return new Place(Side.NEAREST, null, words, null, null);
    }

    /** The copy inside the box spanning {@code one} and {@code other}. */
    public static Place between(ImageTemplate one, ImageTemplate other) {
        return new Place(Side.BETWEEN, one, null, other, null);
    }

    /** The copy inside the box spanning {@code one} and {@code other}. */
    public static Place between(Caption one, Caption other) {
        return new Place(Side.BETWEEN, null, one, null, other);
    }

    /** How it reads in a sentence: {@code right of "Daily"}, {@code between header and footer}. */
    public String label() {
        String from = said(picture, words);
        return switch (side) {
            case BETWEEN -> "between " + from + " and " + said(otherPicture, otherWords);
            case UNKNOWN -> "somewhere";
            default -> side.displayName().toLowerCase(Locale.ROOT) + " " + from;
        };
    }

    private static String said(ImageTemplate picture, Caption words) {
        return picture != null ? picture.id() : words != null ? words.said() : "?";
    }
}
