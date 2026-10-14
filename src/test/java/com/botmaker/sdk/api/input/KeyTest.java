package com.botmaker.sdk.api.input;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

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
    void a_key_is_named_as_printed_or_as_the_enum_names_it() {
        assertEquals(Optional.of(Key.ESCAPE), Key.named("Escape"));
        assertEquals(Optional.of(Key.ESCAPE), Key.named("esc"));
        assertEquals(Optional.of(Key.Q), Key.named("q"));
        assertEquals(Optional.of(Key.NUM5), Key.named("5"));
        assertEquals(Optional.of(Key.PAGE_UP), Key.named("Page Up"));
        assertEquals(Optional.of(Key.PAGE_UP), Key.named("PAGE_UP"));
        assertEquals(Optional.of(Key.F5), Key.named("f5"));
        assertEquals(Optional.of(Key.LEFT_BRACKET), Key.named("["));
        assertEquals(Optional.empty(), Key.named("Hyper"));
        assertEquals(Optional.empty(), Key.named(" "));
    }

    @Test
    void numpad_enter_has_no_windows_key_of_its_own() {
        assertEquals(Key.ENTER.windowsVk(), Key.NUMPAD_ENTER.windowsVk());
        assertEquals(0xFF8D, Key.NUMPAD_ENTER.linuxKeySym());
    }
}
