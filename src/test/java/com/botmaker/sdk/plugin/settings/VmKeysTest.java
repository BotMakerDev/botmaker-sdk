package com.botmaker.sdk.plugin.settings;

import javafx.scene.input.KeyCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The Windows virtual key a key pressed over the VM's screen sends. */
class VmKeysTest {

    @Test
    void keysWhoseCodesAgreePassThrough() {
        assertEquals('A', VmKeys.virtualKey(KeyCode.A.getCode()));
        assertEquals('7', VmKeys.virtualKey(KeyCode.DIGIT7.getCode()));
        assertEquals(0x70, VmKeys.virtualKey(KeyCode.F1.getCode()));
        assertEquals(0x25, VmKeys.virtualKey(KeyCode.LEFT.getCode()));
        assertEquals(0x1B, VmKeys.virtualKey(KeyCode.ESCAPE.getCode()));
        assertEquals(0x10, VmKeys.virtualKey(KeyCode.SHIFT.getCode()));
        assertEquals(0x60, VmKeys.virtualKey(KeyCode.NUMPAD0.getCode()));
    }

    @Test
    void keysWhoseCodesDifferAreMapped() {
        assertEquals(0x0D, VmKeys.virtualKey(KeyCode.ENTER.getCode()));
        assertEquals(0x2E, VmKeys.virtualKey(KeyCode.DELETE.getCode()));
        assertEquals(0x2D, VmKeys.virtualKey(KeyCode.INSERT.getCode()));
        assertEquals(0x5B, VmKeys.virtualKey(KeyCode.WINDOWS.getCode()));
        assertEquals(0xBC, VmKeys.virtualKey(KeyCode.COMMA.getCode()));
        assertEquals(0xBA, VmKeys.virtualKey(KeyCode.SEMICOLON.getCode()));
        assertEquals(0xDC, VmKeys.virtualKey(KeyCode.BACK_SLASH.getCode()));
        assertEquals(0xDE, VmKeys.virtualKey(KeyCode.QUOTE.getCode()));
    }
}
