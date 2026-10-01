package com.botmaker.sdk.plugin.editors;

import com.botmaker.sdk.api.input.Key;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static com.botmaker.sdk.api.input.Key.*;

/**
 * A full-size keyboard as rows of caps, widths in key units: the function row, the main block, the navigation
 * cluster with the arrows, and the numpad. Pure data. A {@link Key} constant this table has no cap for is put
 * in a last row of its own, so a key added to the SDK is still drawn before anyone places it.
 *
 * <p><b>Three boards (feedback 3, 2026-09-27)</b>: QWERTY, AZERTY and QWERTZ differ in where the letters and
 * the punctuation beside them sit; the function row, the navigation cluster and the numpad are shared. <b>A cap
 * writes its own key on every board</b>: the cap labelled A writes {@code Key.A} wherever it sits. A {@code Key}
 * is the key's value, and the operating system maps it through the user's layout when the bot presses it; a
 * game that reads physical positions (scancodes) is out of scope.
 */
final class KeyboardLayout {

    /** A cap, or a gap when {@code key} is null. */
    record Cap(Key key, double width) {}

    /** The keyboards a person may be looking at while picking. {@link #fromId} is total: QWERTY for anything else. */
    enum Board {
        QWERTY("qwerty", "QWERTY"),
        AZERTY("azerty", "AZERTY"),
        QWERTZ("qwertz", "QWERTZ");

        private final String id;
        private final String displayName;

        Board(String id, String displayName) {
            this.id = id;
            this.displayName = displayName;
        }

        String id() {
            return id;
        }

        String displayName() {
            return displayName;
        }

        static Board fromId(String id) {
            for (Board board : values()) if (board.id.equalsIgnoreCase(id == null ? "" : id.strip())) return board;
            return QWERTY;
        }
    }

    /** The width of the widest row, in key units: the number row, from ` to the numpad's −. */
    static final double WIDTH = 23;

    /** The smallest and largest a one-unit cap is drawn, in pixels. */
    static final double MIN_UNIT = 22;
    static final double MAX_UNIT = 48;

    /** The smallest a cap's text is drawn, in pixels; a face that needs less is still this size. */
    static final double MIN_FONT = 7;

    private static final Map<Board, List<List<Cap>>> ROWS = new EnumMap<>(Board.class);

    static {
        for (Board board : Board.values()) ROWS.put(board, build(board));
    }

    private KeyboardLayout() {}

    /** The QWERTY board. */
    static List<List<Cap>> rows() {
        return rows(Board.QWERTY);
    }

    static List<List<Cap>> rows(Board board) {
        return ROWS.get(board);
    }

    /**
     * What a cap shows: its label, short enough for the cap. A numpad cap is its own face ("7", "+"): the "Num"
     * the label carries is what the chip and the tooltip say, and on the cap it cut the digit off ("N…"). A
     * label of two words is two lines; the three too long for a line are shortened.
     */
    static String face(Key key) {
        return switch (key) {
            case BACKSPACE -> "Back\nspace";
            case INSERT -> "Ins";
            case DELETE -> "Del";
            case NUMPAD_ENTER -> "Enter";
            default -> {
                String label = key.label();
                if (key.name().startsWith("NUMPAD_")) label = label.substring("Num ".length());
                yield label.replace(' ', '\n');
            }
        };
    }

    /**
     * The font size, in pixels, that fits {@code face} on a cap {@code cap} pixels square: the unit's own size
     * for one character, smaller for a longer line or a second line — so a face shrinks before it is cut —
     * and never under {@link #MIN_FONT}. A character is taken as 0.6 of the font wide and a line as 1.3 tall.
     */
    static double fontFor(double cap, String face) {
        String[] lines = face.split("\n");
        int longest = 1;
        for (String line : lines) longest = Math.max(longest, line.length());
        double room = cap - 4;
        double size = Math.min(cap * 0.36, Math.min(room / (0.6 * longest), room / (1.3 * lines.length)));
        return Math.max(MIN_FONT, size);
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

    /**
     * The three letter rows' keys, top to bottom, as each board has them; everything else is shared. The counts
     * match QWERTY's row for row (12, 11, 10), so every board is the same shape. AZERTY's ^ $ ù * &lt; and ! are
     * keys the SDK has no constant for, so its punctuation keys fill those places in their US order.
     */
    private static List<List<Key>> letters(Board board) {
        return switch (board) {
            case QWERTY -> List.of(
                    List.of(Q, W, E, R, T, Y, U, I, O, P, LEFT_BRACKET, RIGHT_BRACKET),
                    List.of(A, S, D, F, G, H, J, K, L, SEMICOLON, QUOTE),
                    List.of(Z, X, C, V, B, N, M, COMMA, PERIOD, SLASH));
            case AZERTY -> List.of(
                    List.of(A, Z, E, R, T, Y, U, I, O, P, LEFT_BRACKET, RIGHT_BRACKET),
                    List.of(Q, S, D, F, G, H, J, K, L, M, QUOTE),
                    List.of(W, X, C, V, B, N, COMMA, SEMICOLON, PERIOD, SLASH));
            case QWERTZ -> List.of(
                    List.of(Q, W, E, R, T, Z, U, I, O, P, LEFT_BRACKET, RIGHT_BRACKET),
                    List.of(A, S, D, F, G, H, J, K, L, SEMICOLON, QUOTE),
                    List.of(Y, X, C, V, B, N, M, COMMA, PERIOD, SLASH));
        };
    }

    private static List<Cap> caps(List<Key> keys) {
        return keys.stream().map(KeyboardLayout::k).toList();
    }

    private static List<Cap> row(List<Cap> start, List<Cap> middle, List<Cap> end) {
        List<Cap> out = new ArrayList<>(start);
        out.addAll(middle);
        out.addAll(end);
        return List.copyOf(out);
    }

    private static List<List<Cap>> build(Board board) {
        List<List<Key>> letters = letters(board);
        List<List<Cap>> rows = new ArrayList<>(List.of(
                List.of(k(ESCAPE), gap(1), k(F1), k(F2), k(F3), k(F4), gap(0.5), k(F5), k(F6), k(F7), k(F8),
                        gap(0.5), k(F9), k(F10), k(F11), k(F12)),
                List.of(k(BACKQUOTE), k(NUM1), k(NUM2), k(NUM3), k(NUM4), k(NUM5), k(NUM6), k(NUM7), k(NUM8),
                        k(NUM9), k(NUM0), k(MINUS), k(EQUALS), k(BACKSPACE, 2), gap(0.5),
                        k(INSERT), k(HOME), k(PAGE_UP), gap(0.5),
                        k(NUM_LOCK), k(NUMPAD_DIVIDE), k(NUMPAD_MULTIPLY), k(NUMPAD_SUBTRACT)),
                row(List.of(k(TAB, 1.5)), caps(letters.get(0)),
                        List.of(k(BACKSLASH, 1.5), gap(0.5), k(DELETE), k(END), k(PAGE_DOWN), gap(0.5),
                                k(NUMPAD_7), k(NUMPAD_8), k(NUMPAD_9), k(NUMPAD_ADD))),
                row(List.of(k(CAPS_LOCK, 1.75)), caps(letters.get(1)),
                        List.of(k(ENTER, 2.25), gap(4), k(NUMPAD_4), k(NUMPAD_5), k(NUMPAD_6), gap(1))),
                row(List.of(k(SHIFT, 2.25)), caps(letters.get(2)),
                        List.of(gap(4.25), k(UP), gap(1.5), k(NUMPAD_1), k(NUMPAD_2), k(NUMPAD_3), k(NUMPAD_ENTER))),
                List.of(k(CTRL, 1.5), k(META, 1.25), k(ALT, 1.25), k(SPACE, 6.25), gap(4.75),
                        k(LEFT), k(DOWN), k(RIGHT), gap(0.5),
                        k(NUMPAD_0, 2), k(NUMPAD_DECIMAL), gap(1))));
        Set<Key> placed = EnumSet.noneOf(Key.class);
        rows.forEach(row -> row.forEach(cap -> {
            if (cap.key() != null) placed.add(cap.key());
        }));
        // The keys with no place on the board, as many rows as they need: one long row was wider than the
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
