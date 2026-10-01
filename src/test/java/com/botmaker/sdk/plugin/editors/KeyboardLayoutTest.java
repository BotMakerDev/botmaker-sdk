package com.botmaker.sdk.plugin.editors;

import com.botmaker.sdk.api.input.Key;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeyboardLayoutTest {

    @Test
    void every_key_the_sdk_has_is_on_the_keyboard_exactly_once() {
        List<Key> caps = KeyboardLayout.rows().stream().flatMap(List::stream)
                .map(KeyboardLayout.Cap::key).filter(Objects::nonNull).toList();
        assertEquals(EnumSet.allOf(Key.class), EnumSet.copyOf(caps), "missing a key");
        assertEquals(caps.size(), new HashSet<>(caps).size(), "a key drawn twice");
    }

    @Test
    void no_row_is_wider_than_the_keyboard_so_no_cap_is_pushed_out_of_the_window() {
        for (KeyboardLayout.Board board : KeyboardLayout.Board.values()) {
            for (List<KeyboardLayout.Cap> row : KeyboardLayout.rows(board)) {
                double width = row.stream().mapToDouble(KeyboardLayout.Cap::width).sum();
                assertTrue(width <= KeyboardLayout.WIDTH, board + ": " + row + " is " + width + " units");
            }
        }
    }

    /**
     * Every layout holds every key once, so none loses a key the QWERTY one has; only the letters and the
     * punctuation beside them move. The cap still writes its own key (feedback 3).
     */
    @Test
    void every_layout_holds_every_key_exactly_once() {
        for (KeyboardLayout.Board board : KeyboardLayout.Board.values()) {
            List<Key> caps = KeyboardLayout.rows(board).stream().flatMap(List::stream)
                    .map(KeyboardLayout.Cap::key).filter(Objects::nonNull).toList();
            assertEquals(EnumSet.allOf(Key.class), EnumSet.copyOf(caps), board + " is missing a key");
            assertEquals(caps.size(), new HashSet<>(caps).size(), board + " draws a key twice");
        }
    }

    @Test
    void the_layouts_put_their_letters_where_their_keyboards_do() {
        assertEquals(Key.A, firstLetterOfRow(KeyboardLayout.Board.AZERTY, 2));
        assertEquals(Key.Q, firstLetterOfRow(KeyboardLayout.Board.AZERTY, 3));
        assertEquals(Key.W, firstLetterOfRow(KeyboardLayout.Board.AZERTY, 4));
        assertEquals(Key.Y, firstLetterOfRow(KeyboardLayout.Board.QWERTZ, 4));
        assertEquals(Key.Q, firstLetterOfRow(KeyboardLayout.Board.QWERTY, 2));
        assertEquals(KeyboardLayout.Board.QWERTY, KeyboardLayout.Board.fromId("nope"));
        assertEquals(KeyboardLayout.Board.AZERTY, KeyboardLayout.Board.fromId("azerty"));
    }

    private static Key firstLetterOfRow(KeyboardLayout.Board board, int row) {
        return KeyboardLayout.rows(board).get(row).stream().map(KeyboardLayout.Cap::key)
                .filter(k -> k != null && k.name().length() == 1).findFirst().orElseThrow();
    }

    /** A cap shows its short face: "7" on the numpad, never "Num 7" cut to "N…" (feedback 3). */
    @Test
    void every_cap_face_fits_five_characters_a_line() {
        for (Key key : Key.values()) {
            for (String line : KeyboardLayout.face(key).split("\n")) {
                assertTrue(!line.isEmpty() && line.length() <= 5, key + " shows \"" + line + "\"");
            }
        }
        assertEquals("7", KeyboardLayout.face(Key.NUMPAD_7));
        assertEquals("+", KeyboardLayout.face(Key.NUMPAD_ADD));
        assertEquals("Num\nLock", KeyboardLayout.face(Key.NUM_LOCK));
        assertEquals("Enter", KeyboardLayout.face(Key.NUMPAD_ENTER));
    }

    /** A cap's font shrinks for a long face before anything is cut, and never grows past the unit's own. */
    @Test
    void a_long_face_gets_a_smaller_font() {
        double one = KeyboardLayout.fontFor(40, "A");
        double five = KeyboardLayout.fontFor(40, "Shift");
        double twoLines = KeyboardLayout.fontFor(40, "Num\nLock");
        assertTrue(five < one, five + " vs " + one);
        assertTrue(twoLines < one);
        assertTrue(KeyboardLayout.fontFor(40, "Shift") * 0.6 * 5 <= 40, "five letters fit the cap");
        assertTrue(KeyboardLayout.fontFor(22, "Space") >= 7, "never below readable");
    }

    @Test
    void the_cap_size_follows_the_window_between_two_limits() {
        assertEquals(30, KeyboardLayout.unitFor(30 * KeyboardLayout.WIDTH));
        assertEquals(KeyboardLayout.MIN_UNIT, KeyboardLayout.unitFor(100));
        assertEquals(KeyboardLayout.MAX_UNIT, KeyboardLayout.unitFor(10_000));
    }

    @Test
    void a_search_matches_the_cap_or_the_constant_name() {
        assertEquals(Set.of(Key.PAGE_UP, Key.PAGE_DOWN), KeyboardLayout.matching("page"));
        assertTrue(KeyboardLayout.matching("num 5").contains(Key.NUMPAD_5));
        assertTrue(KeyboardLayout.matching("esc").contains(Key.ESCAPE));
        assertEquals(EnumSet.allOf(Key.class), KeyboardLayout.matching("  "));
    }
}
