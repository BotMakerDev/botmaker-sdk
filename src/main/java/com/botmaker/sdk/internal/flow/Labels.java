package com.botmaker.sdk.internal.flow;

import java.util.Locale;

/**
 * What an activity or an outcome is called on the canvas and in the trace: its constant's name read as words,
 * {@code NOTHING_LEFT} as "Nothing left" (2026-10-10).
 *
 * <p>The name is the whole of an {@code Activities} or {@code Outcomes} constant — a bot's enum has nothing else
 * to hold a label in — so the label is derived, never stored. Each {@code _} is a space, and so, in a name with
 * a lower-case letter, is each step from a lower-case letter or digit to an upper-case one; the words are
 * lower-cased and the first letter capitalised. {@code FlowNames.constantFor} goes the other way, and the two
 * agree on every constant of upper-case letters, digits and single {@code _}s — {@code STAGE_2B} is
 * "Stage 2b" and back. A constant written otherwise ({@code bagFull}) is shown, but a card cannot name it back.
 */
public final class Labels {

    private Labels() {}

    /** {@code constant} as words: {@code BAG_FULL} is "Bag full", {@code HP_BELOW_50} "Hp below 50". */
    public static String of(String constant) {
        if (constant == null) return "";
        boolean camel = !constant.equals(constant.toUpperCase(Locale.ROOT));
        StringBuilder out = new StringBuilder();
        char previous = 0;
        for (char c : constant.toCharArray()) {
            boolean step = c == '_' || camel && Character.isUpperCase(c)
                    && (Character.isLowerCase(previous) || Character.isDigit(previous));
            if (step && !out.isEmpty() && out.charAt(out.length() - 1) != ' ') out.append(' ');
            if (c != '_') out.append(Character.toLowerCase(c));
            previous = c;
        }
        String words = out.toString().strip();
        return words.isEmpty() ? "" : words.substring(0, 1).toUpperCase(Locale.ROOT) + words.substring(1);
    }
}
