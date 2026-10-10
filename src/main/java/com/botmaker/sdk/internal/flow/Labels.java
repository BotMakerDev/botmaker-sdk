package com.botmaker.sdk.internal.flow;

import java.util.Locale;

/**
 * What an activity or an outcome is called on the canvas and in the trace: its constant's name read as words,
 * {@code NOTHING_LEFT} as "Nothing left" (2026-10-10).
 *
 * <p>The name is the whole of an activity's enum and of each of its constants — a bot's enum has nothing else
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

    /**
     * An activity's enum as the canvas labels it: {@code DailyReward} as "Daily reward" — or, when those words
     * would name another enum ({@link #typeOf} of "Xpfarm" is not {@code XPFarm}), the name itself. So a card's
     * label always names its enum back, and an enum named in capitals keeps its name through a save.
     */
    public static String ofType(String type) {
        if (type == null) return "";
        String words = of(type);
        return type.equals(typeOf(words)) ? words : type;
    }

    /**
     * The enum a typed activity label names — {@code "Daily reward"} to {@code DailyReward}, {@code "DailyReward"}
     * to itself — or null when it can be none (no letter, or a digit first).
     *
     * <p>A label that is already a Java name starting with a capital ({@code "XPFarm"}, {@code "COLLECT"},
     * {@code "Daily_Reward"}) is that name, so {@code typeOf(ofType(name))} is {@code name} for every such enum.
     * Otherwise each run of letters and digits is a word, its first letter upper-cased; a word typed all in
     * capitals ({@code "BAG full"}) is lower-cased after its first letter, and any other keeps its case.
     */
    public static String typeOf(String label) {
        if (label == null) return null;
        String stripped = label.strip();
        if (isTypeName(stripped)) return stripped;
        StringBuilder out = new StringBuilder();
        for (String word : label.strip().split("[^\\p{L}\\p{N}]+")) {
            if (word.isEmpty()) continue;
            String rest = word.substring(1);
            if (rest.equals(rest.toUpperCase(Locale.ROOT))) rest = rest.toLowerCase(Locale.ROOT);
            out.append(Character.toUpperCase(word.charAt(0))).append(rest);
        }
        if (out.isEmpty() || !Character.isJavaIdentifierStart(out.charAt(0))) return null;
        return out.toString();
    }

    private static boolean isTypeName(String s) {
        if (s.isEmpty() || !Character.isUpperCase(s.charAt(0))) return false;
        for (int i = 1; i < s.length(); i++) {
            if (!Character.isJavaIdentifierPart(s.charAt(i)) || s.charAt(i) == '$') return false;
        }
        return true;
    }
}
