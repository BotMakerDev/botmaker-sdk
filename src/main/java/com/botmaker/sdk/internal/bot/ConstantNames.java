// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

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
     * step from a lower-case letter or digit to an upper-case one, and the last capital of a run before a
     * lower-case letter ({@code "BookADemo"} to {@code BOOK_A_DEMO}, {@code "HTTPServer"} to {@code HTTP_SERVER}).
     * So an identifier label that is already a constant ({@code NOTHING_LEFT}) is its own constant.
     */
    public static String constantFor(String label) {
        if (label == null) return null;
        StringBuilder out = new StringBuilder();
        boolean gap = false;
        char previous = 0;
        char[] chars = label.strip().toCharArray();
        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            if (!Character.isLetterOrDigit(c)) {
                gap = !out.isEmpty();
                continue;
            }
            char next = i + 1 < chars.length ? chars[i + 1] : 0;
            boolean camelStep = Character.isUpperCase(c)
                    && (Character.isLowerCase(previous) || Character.isDigit(previous)
                    || Character.isUpperCase(previous) && Character.isLowerCase(next));
            if (!out.isEmpty() && (gap || camelStep)) out.append('_');
            out.append(Character.toUpperCase(c));
            gap = false;
            previous = c;
        }
        if (out.isEmpty() || !Character.isJavaIdentifierStart(out.charAt(0))) return null;
        return out.toString();
    }
}
