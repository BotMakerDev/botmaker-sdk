package com.botmaker.sdk.api.screens;

/**
 * A way this game shows a button other than as it was cut, learned on the game and applied to every button: the
 * button's usual picture changed by its colour alone — {@code shown = gain × cut + offset} in each channel, 0 to 1.
 * A button matched by its usual look whose pixels fit a shade shows that shade's {@link #state()}, so one button seen
 * dimmed teaches a run that every dimmed button is {@code Disabled}, with nothing else cut (2026-11-02).
 *
 * <p>Learned, never chosen: Discovery and the assistant write it from a button seen both ways, and its state is what
 * that button did when pressed. A shade whose cause is the bot's own input ({@link Cause#HOVER}, {@link Cause#PRESS},
 * {@link Cause#FOCUS}) means {@link State#NORMAL} and is tried only on the button the input is on.
 *
 * <p>Three kinds change where and how big the button shows rather than its colour ({@link Kind#GLOW},
 * {@link Kind#SCALE}, {@link Kind#SHIFT}): their {@link #geometry()} says how, in the pixels of the frame the button
 * was cut from, scaled to the frame as places are.
 *
 * @param kind      what the change looks like; for a colour change, for the reader only: the fit is what tells it
 * @param gain      how much of each channel of the cut shows
 * @param offset    what each channel gains on top, 0 to 1; for a {@link Kind#GLOW}, how much lighter its ring is than
 *                  the frame around it
 * @param tolerance how far a fit may stray from {@code gain} and {@code offset}, in either, and still be this shade; a
 *                  scale's share and a shift's share of the button's size too
 * @param cause     what makes the game show it
 * @param state     what the button means shown so; {@code Normal} for a shade the bot's input causes
 * @param geometry  how a glow, a scale or a shift moves the button; {@link Geometry#NONE} for a colour change
 */
public record Shade(Kind kind, Channels gain, Channels offset, double tolerance, Cause cause, ButtonState state,
                    Geometry geometry) {

    /** How far a learned fit may stray when nothing else is said: a frame's noise and a game's soft edges. */
    public static final double TOLERANCE = 0.08;

    public Shade {
        kind = kind == null ? Kind.DIM : kind;
        gain = gain == null ? Channels.all(1) : gain;
        offset = offset == null ? Channels.all(0) : offset;
        tolerance = tolerance <= 0 ? TOLERANCE : tolerance;
        cause = cause == null ? Cause.GAME : cause;
        // The bot's own input changes how a button looks, never what it means.
        state = cause != Cause.GAME || state == null ? State.NORMAL : state;
        geometry = geometry == null ? Geometry.NONE : geometry;
    }

    /** Whether this shade moves the button rather than changing its colour. */
    public boolean moves() {
        return kind == Kind.GLOW || kind == Kind.SCALE || kind == Kind.SHIFT;
    }

    /**
     * How a shade moves the button, in the pixels of the frame it was cut from.
     *
     * @param ring     a {@link Kind#GLOW}'s ring width around the button; 0 for none
     * @param smallest a {@link Kind#SCALE}'s smallest size, 1 the cut's: a pulse's one end
     * @param largest  its largest size: a pulse's other end, {@code smallest} again for a button that only grows
     * @param dx       a {@link Kind#SHIFT}'s move to the right of the button's usual place
     * @param dy       its move down
     */
    public record Geometry(int ring, double smallest, double largest, int dx, int dy) {

        /** No move: a colour change. */
        public static final Geometry NONE = new Geometry(0, 1, 1, 0, 0);

        public Geometry {
            ring = Math.max(0, ring);
            smallest = smallest <= 0 ? 1 : smallest;
            largest = largest <= 0 ? smallest : largest;
            if (largest < smallest) {
                double low = largest;
                largest = smallest;
                smallest = low;
            }
        }
    }

    /** One value per colour channel. */
    public record Channels(double red, double green, double blue) {

        /** The same value in every channel. */
        public static Channels all(double value) {
            return new Channels(value, value, value);
        }

        /** The value of channel {@code c}: 0 red, 1 green, 2 blue. */
        public double get(int c) {
            return switch (c) {
                case 0 -> red;
                case 1 -> green;
                default -> blue;
            };
        }
    }

    /** What a shade's change looks like. */
    public enum Kind {
        /** Darker, its colour kept: every channel scaled down alike. */
        DIM("dimmed"),
        /** Its colour gone: each channel follows the cut's light, not its own channel. */
        GREY("greyed"),
        /** Lighter: every channel scaled up or lifted alike. */
        BRIGHT("brightened"),
        /** Coloured: the channels scaled apart. */
        TINT("tinted"),
        /** Faded towards a background: scaled down and lifted towards its colour. */
        FADE("faded"),
        /** A light around it: a ring of {@link Geometry#ring()} lighter than the frame beyond by the offset. */
        GLOW("glowing"),
        /** Bigger or smaller, or pulsing between two sizes: matched at each learned size. */
        SCALE("scaled"),
        /** Moved off its usual place by {@link Geometry#dx()}, {@link Geometry#dy()}. */
        SHIFT("shifted");

        private final String displayName;

        Kind(String displayName) {
            this.displayName = displayName;
        }

        /** {@code "dimmed"}: how a line says a button shows. */
        public String displayName() {
            return displayName;
        }
    }

    /** What makes the game show a shade. */
    public enum Cause {
        /** The game, on its own: what the shade means is the game's. */
        GAME("the game"),
        /** The cursor resting on the button. An emulator has none. */
        HOVER("hover"),
        /** The button held down. */
        PRESS("press"),
        /** The button picked by the keyboard, or last pressed. */
        FOCUS("focus");

        private final String displayName;

        Cause(String displayName) {
            this.displayName = displayName;
        }

        public String displayName() {
            return displayName;
        }
    }
}
