package com.botmaker.sdk.api.interaction;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KeyTest {

    /** Appended, so a constant a bot already names keeps its ordinal. */
    @Test
    void the_first_sixty_two_keep_their_place() {
        assertEquals(0, Key.A.ordinal());
        assertEquals(61, Key.DOWN.ordinal());
        assertEquals(62, Key.BACKQUOTE.ordinal());
        assertEquals(96, Key.values().length);
    }

    @Test
    void a_key_reads_as_it_is_printed_on_the_cap() {
        assertEquals(List.of("Ctrl", "S", "5", "F5", "Page Up", "[", "Num 5", "Num +", "Num Enter", "Esc", "Caps Lock"),
                List.of(Key.CTRL.label(), Key.S.label(), Key.NUM5.label(), Key.F5.label(), Key.PAGE_UP.label(),
                        Key.LEFT_BRACKET.label(), Key.NUMPAD_5.label(), Key.NUMPAD_ADD.label(),
                        Key.NUMPAD_ENTER.label(), Key.ESCAPE.label(), Key.CAPS_LOCK.label()));
    }

    @Test
    void numpad_enter_has_no_windows_key_of_its_own() {
        assertEquals(Key.ENTER.windowsVk(), Key.NUMPAD_ENTER.windowsVk());
        assertEquals(0xFF8D, Key.NUMPAD_ENTER.linuxKeySym());
    }
}
