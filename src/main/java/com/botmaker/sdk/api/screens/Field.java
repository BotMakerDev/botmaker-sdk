// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.api.screens;

import com.botmaker.sdk.internal.screens.FieldText;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * A check of what a text field holds now, read from the screen's tree when it is asked (2026-11-19): the rule a form
 * keeps, written as the {@code .when(…)} of the input it enables.
 *
 * <pre>{@code
 * .when(Condition.all(Field.valid(() -> Screens.SIGN_UP.email, Field.Kind.EMAIL),
 *         Field.atLeast(() -> Screens.SIGN_UP.password, 8)))
 * }</pre>
 *
 * <p>The field is named lazily, as screens name each other ({@code Do.over}): a rule on a screen's input names its
 * sibling fields, and the screen's constant is not set while its fields are made. It is an input found by its
 * {@link Element}; its text is what the tree states, a website's field its value.
 * A check says {@link Answer#UNKNOWN} when it cannot read that text: the input is found by a picture, the target shows
 * no tree, the tree could not be read, or the element is not found there exactly once. An unknown never closes a way.
 * A password's text is the bullets it shows, as many as its characters: only {@link #atLeast} and {@link #filled} tell
 * anything of it, every other check is unknown there.
 */
public sealed interface Field extends Condition {

    /** The text field it checks, named lazily. */
    Supplier<Button> input();

    /** What it says of {@code text}, the field's text. */
    Answer says(String text);

    /** What it says of the field's text now; unknown when it cannot be read, or is a password's bullets it cannot. */
    @Override
    default Answer answer() {
        return FieldText.now(input().get())
                .map(read -> read.masked() && !(this instanceof Filled || this instanceof AtLeast)
                        ? Answer.UNKNOWN : says(read.text()))
                .orElse(Answer.UNKNOWN);
    }

    /** Yes when {@code input} holds any text. */
    static Field filled(Supplier<Button> input) {
        return new Filled(input);
    }

    /** Yes when {@code input} holds a valid value of {@code kind}, as a browser judges one; empty is not valid. */
    static Field valid(Supplier<Button> input, Kind kind) {
        return new Valid(input, kind);
    }

    /** Yes when {@code input}'s text holds {@code text}: {@code Field.holds(() -> Screens.SIGN_UP.email, "@")}. */
    static Field holds(Supplier<Button> input, String text) {
        return new Holds(input, text);
    }

    /** Yes when {@code input}'s text does not hold {@code text}: {@code Field.lacks(() -> Screens.SIGN_UP.email, "@")}. */
    static Field lacks(Supplier<Button> input, String text) {
        return new Lacks(input, text);
    }

    /** Yes when {@code input} holds {@code characters} characters or more. */
    static Field atLeast(Supplier<Button> input, int characters) {
        return new AtLeast(input, characters);
    }

    /**
     * Yes when {@code input} holds a number from {@code min} to {@code max}, both in; text that is no number is not in
     * it. An open side is the largest double: {@code inRange(() -> Screens.SIGN_UP.age, 18, Double.MAX_VALUE)}.
     */
    static Field inRange(Supplier<Button> input, double min, double max) {
        return new InRange(input, min, max);
    }

    /** What a field's value is, where a browser says whether one is valid. */
    enum Kind {
        EMAIL("email", "email"),
        URL("url", "URL"),
        NUMBER("number", "number"),
        UNKNOWN("unknown", "value");

        /** A browser's {@code type="email"} test: a name, an at sign and a domain of labels, the dot not needed. */
        private static final Pattern EMAIL_ADDRESS = Pattern.compile("[a-zA-Z0-9.!#$%&'*+/=?^_`{|}~-]+@"
                + "[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(?:\\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*");

        /** A browser's valid floating-point number: no plus sign, no point without digits after it, no spaces. */
        private static final Pattern NUMBER_VALUE = Pattern.compile("-?(?:\\d+(?:\\.\\d+)?|\\.\\d+)(?:[eE][-+]?\\d+)?");

        private final String id;
        private final String displayName;

        Kind(String id, String displayName) {
            this.id = id;
            this.displayName = displayName;
        }

        /** Its input type, {@code "email"}. */
        public String id() {
            return id;
        }

        /** How a sentence names it: "a valid email". */
        public String displayName() {
            return displayName;
        }

        /** The kind whose {@link #id} is {@code id}, case and spaces aside; {@link #UNKNOWN} for any other. */
        public static Kind fromId(String id) {
            for (Kind kind : values()) {
                if (kind != UNKNOWN && kind.id.equalsIgnoreCase(id == null ? "" : id.strip())) return kind;
            }
            return UNKNOWN;
        }

        /**
         * Whether {@code text} is a valid value of this kind. An email's and a URL's spaces at their ends are dropped
         * first, as a browser drops them from the value.
         */
        boolean judges(String text) {
            return switch (this) {
                case EMAIL -> EMAIL_ADDRESS.matcher(text.strip()).matches();
                case URL -> absolute(text.strip());
                case NUMBER -> number(text) != null;
                case UNKNOWN -> false;
            };
        }

        private static boolean absolute(String text) {
            try {
                return new URI(text).isAbsolute();
            } catch (URISyntaxException e) {
                return false;
            }
        }
    }

    /** {@link #filled}. */
    record Filled(Supplier<Button> input) implements Field {
        public Filled {
            Objects.requireNonNull(input, "input");
        }

        @Override
        public Answer says(String text) {
            return Answer.of(!text.isEmpty());
        }
    }

    /** {@link #valid}: of a kind a browser judges, never {@link Kind#UNKNOWN}, which would never answer. */
    record Valid(Supplier<Button> input, Kind kind) implements Field {
        public Valid {
            Objects.requireNonNull(input, "input");
            if (kind == null || kind == Kind.UNKNOWN) {
                throw new IllegalArgumentException("A valid value is of a kind: an email, a URL or a number");
            }
        }

        @Override
        public Answer says(String text) {
            return Answer.of(kind.judges(text));
        }
    }

    /** {@link #holds}. */
    record Holds(Supplier<Button> input, String text) implements Field {
        public Holds {
            Objects.requireNonNull(input, "input");
            some(text);
        }

        @Override
        public Answer says(String said) {
            return Answer.of(said.contains(text));
        }
    }

    /** {@link #lacks}. */
    record Lacks(Supplier<Button> input, String text) implements Field {
        public Lacks {
            Objects.requireNonNull(input, "input");
            some(text);
        }

        @Override
        public Answer says(String said) {
            return Answer.of(!said.contains(text));
        }
    }

    /** {@link #atLeast}: characters, not the UTF-16 units a string counts. */
    record AtLeast(Supplier<Button> input, int characters) implements Field {
        public AtLeast {
            Objects.requireNonNull(input, "input");
            if (characters < 1) throw new IllegalArgumentException("A field holds at least one character, or is filled");
        }

        @Override
        public Answer says(String text) {
            return Answer.of(text.codePointCount(0, text.length()) >= characters);
        }
    }

    /** {@link #inRange}. */
    record InRange(Supplier<Button> input, double min, double max) implements Field {
        public InRange {
            Objects.requireNonNull(input, "input");
            // An open side is Double.MAX_VALUE: an infinity is no number the bot's Java can write back.
            if (!Double.isFinite(min) || !Double.isFinite(max) || min > max) {
                throw new IllegalArgumentException("A range runs from its least number to its most: " + min + " to "
                        + max);
            }
        }

        @Override
        public Answer says(String text) {
            Double n = number(text);
            return Answer.of(n != null && n >= min && n <= max);
        }
    }

    /** {@code text} as a browser reads a number field's value; null when it is none, or too large to be one. */
    private static Double number(String text) {
        if (!Kind.NUMBER_VALUE.matcher(text).matches()) return null;
        double n = Double.parseDouble(text);
        return Double.isFinite(n) ? n : null;
    }

    private static void some(String text) {
        if (text == null || text.isEmpty()) throw new IllegalArgumentException("A field holds some text, not none");
    }
}
