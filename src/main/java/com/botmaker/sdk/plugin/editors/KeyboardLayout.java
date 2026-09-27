package com.botmaker.sdk.plugin.editors;

import com.botmaker.sdk.api.interaction.Key;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static com.botmaker.sdk.api.interaction.Key.*;

/**
 * A full-size US keyboard as rows of caps, widths in key units: the function row, the main block, the
 * navigation cluster with the arrows, and the numpad. Pure data. A {@link Key} constant this table has no cap
 * for is put in a last row of its own, so a key added to the SDK is still drawn before anyone places it.
 */
final class KeyboardLayout {

    /** A cap, or a gap when {@code key} is null. */
    record Cap(Key key, double width) {}

    /** The width of the widest row, in key units: the number row, from ` to the numpad's −. */
    static final double WIDTH = 23;

    /** The smallest and largest a one-unit cap is drawn, in pixels. */
    static final double MIN_UNIT = 22;
    static final double MAX_UNIT = 48;

    private static final List<List<Cap>> ROWS = build();

    private KeyboardLayout() {}

    static List<List<Cap>> rows() {
        return ROWS;
    }

    /**
     * How wide a one-unit cap is on a board {@code width} pixels wide: the whole keyboard fits, within
     * {@link #MIN_UNIT} and {@link #MAX_UNIT} (feedback 2, 2026-09-27 — it was a fixed 34 px, so a small window
     * cut caps off and a large one left the board small).
     */
    static double unitFor(double width) {
        return Math.max(MIN_UNIT, Math.min(MAX_UNIT, width / WIDTH));
    }

    /** The keys whose cap or constant name contains {@code needle}, ignoring case; every key for a blank one. */
    static Set<Key> matching(String needle) {
        String n = needle == null ? "" : needle.trim().toLowerCase(Locale.ROOT);
        Set<Key> out = EnumSet.noneOf(Key.class);
        for (Key key : Key.values()) {
            if (n.isEmpty() || key.label().toLowerCase(Locale.ROOT).contains(n)
                    || key.name().toLowerCase(Locale.ROOT).replace('_', ' ').contains(n)) {
                out.add(key);
            }
        }
        return out;
    }

    private static Cap k(Key key) {
        return new Cap(key, 1);
    }

    private static Cap k(Key key, double width) {
        return new Cap(key, width);
    }

    private static Cap gap(double width) {
        return new Cap(null, width);
    }

    private static List<List<Cap>> build() {
        List<List<Cap>> rows = new ArrayList<>(List.of(
                List.of(k(ESCAPE), gap(1), k(F1), k(F2), k(F3), k(F4), gap(0.5), k(F5), k(F6), k(F7), k(F8),
                        gap(0.5), k(F9), k(F10), k(F11), k(F12)),
                List.of(k(BACKQUOTE), k(NUM1), k(NUM2), k(NUM3), k(NUM4), k(NUM5), k(NUM6), k(NUM7), k(NUM8),
                        k(NUM9), k(NUM0), k(MINUS), k(EQUALS), k(BACKSPACE, 2), gap(0.5),
                        k(INSERT), k(HOME), k(PAGE_UP), gap(0.5),
                        k(NUM_LOCK), k(NUMPAD_DIVIDE), k(NUMPAD_MULTIPLY), k(NUMPAD_SUBTRACT)),
                List.of(k(TAB, 1.5), k(Q), k(W), k(E), k(R), k(T), k(Y), k(U), k(I), k(O), k(P),
                        k(LEFT_BRACKET), k(RIGHT_BRACKET), k(BACKSLASH, 1.5), gap(0.5),
                        k(DELETE), k(END), k(PAGE_DOWN), gap(0.5),
                        k(NUMPAD_7), k(NUMPAD_8), k(NUMPAD_9), k(NUMPAD_ADD)),
                List.of(k(CAPS_LOCK, 1.75), k(A), k(S), k(D), k(F), k(G), k(H), k(J), k(K), k(L),
                        k(SEMICOLON), k(QUOTE), k(ENTER, 2.25), gap(4),
                        k(NUMPAD_4), k(NUMPAD_5), k(NUMPAD_6), gap(1)),
                List.of(k(SHIFT, 2.25), k(Z), k(X), k(C), k(V), k(B), k(N), k(M), k(COMMA), k(PERIOD), k(SLASH),
                        gap(4.25), k(UP), gap(1.5),
                        k(NUMPAD_1), k(NUMPAD_2), k(NUMPAD_3), k(NUMPAD_ENTER)),
                List.of(k(CTRL, 1.5), k(META, 1.25), k(ALT, 1.25), k(SPACE, 6.25), gap(4.75),
                        k(LEFT), k(DOWN), k(RIGHT), gap(0.5),
                        k(NUMPAD_0, 2), k(NUMPAD_DECIMAL), gap(1))));
        Set<Key> placed = EnumSet.noneOf(Key.class);
        rows.forEach(row -> row.forEach(cap -> {
            if (cap.key() != null) placed.add(cap.key());
        }));
        // The keys with no place on a US board, as many rows as they need: one long row was wider than the
        // keyboard and pushed its last caps out of the window.
        List<Cap> spare = new ArrayList<>();
        for (Key key : Key.values()) {
            if (placed.contains(key)) continue;
            spare.add(k(key));
            if (spare.size() == (int) WIDTH) {
                rows.add(List.copyOf(spare));
                spare.clear();
            }
        }
        if (!spare.isEmpty()) rows.add(List.copyOf(spare));
        return List.copyOf(rows);
    }
}
