package com.botmaker.sdk.plugin.editors;

import com.botmaker.sdk.api.interaction.Key;
import javafx.scene.input.KeyCode;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KeyCodesTest {

    @Test
    void the_keys_javafx_reports_map_to_the_sdks() {
        assertEquals(Optional.of(Key.S), KeyCodes.toKey(KeyCode.S));
        assertEquals(Optional.of(Key.NUM5), KeyCodes.toKey(KeyCode.DIGIT5));
        assertEquals(Optional.of(Key.NUMPAD_5), KeyCodes.toKey(KeyCode.NUMPAD5));
        assertEquals(Optional.of(Key.CTRL), KeyCodes.toKey(KeyCode.CONTROL));
        assertEquals(Optional.of(Key.META), KeyCodes.toKey(KeyCode.WINDOWS));
        assertEquals(Optional.of(Key.LEFT_BRACKET), KeyCodes.toKey(KeyCode.OPEN_BRACKET));
        assertEquals(Optional.of(Key.BACKSPACE), KeyCodes.toKey(KeyCode.BACK_SPACE));
    }

    /** A key the SDK lacks writes nothing. */
    @Test
    void a_key_the_sdk_lacks_is_nothing() {
        assertEquals(Optional.empty(), KeyCodes.toKey(KeyCode.PRINTSCREEN));
        assertEquals(Optional.empty(), KeyCodes.toKey(KeyCode.UNDEFINED));
        assertEquals(Optional.empty(), KeyCodes.toKey(null));
    }

    /** JavaFX cannot tell the two Enters apart; every other key is reachable by pressing it. */
    @Test
    void every_key_but_numpad_enter_can_be_pressed() {
        Set<Key> reached = EnumSet.noneOf(Key.class);
        for (KeyCode code : KeyCode.values()) KeyCodes.toKey(code).ifPresent(reached::add);
        assertEquals(Set.of(Key.NUMPAD_ENTER), EnumSet.complementOf(EnumSet.copyOf(reached)));
    }
}
