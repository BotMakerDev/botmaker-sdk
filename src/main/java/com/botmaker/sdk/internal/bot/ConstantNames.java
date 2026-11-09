package com.botmaker.sdk.internal.bot;

/**
 * The constant a typed name is written as: a screen or a picture the author names in words becomes an
 * UPPER_SNAKE constant of the bot's Java. Moved out of the flow names on 2026-11-09, when flows were deleted.
 */
public final class ConstantNames {

    private ConstantNames() {
    }

    /**
     * The constant a label is written as — {@code "Bag full"} to {@code BAG_FULL}, {@code "HandleFullInventory"}
     * to {@code HANDLE_FULL_INVENTORY} — or null when it can be none (no letter, or a digit first).
     *
     * <p>Letters and digits are kept, upper-cased; every run of anything else is one {@code _}, and so is a
     * step from a lower-case letter or digit to an upper-case one. So an identifier label that is already a
     * constant ({@code NOTHING_LEFT}) is its own constant.
     */
    public static String constantFor(String label) {
        if (label == null) return null;
        StringBuilder out = new StringBuilder();
        boolean gap = false;
        char previous = 0;
        for (char c : label.strip().toCharArray()) {
            if (!Character.isLetterOrDigit(c)) {
                gap = !out.isEmpty();
                continue;
            }
            boolean camelStep = Character.isUpperCase(c)
                    && (Character.isLowerCase(previous) || Character.isDigit(previous));
            if (!out.isEmpty() && (gap || camelStep)) out.append('_');
            out.append(Character.toUpperCase(c));
            gap = false;
            previous = c;
        }
        if (out.isEmpty() || !Character.isJavaIdentifierStart(out.charAt(0))) return null;
        return out.toString();
    }
}
