package com.botmaker.sdk.plugin.settings;

import java.util.Map;

/**
 * The Windows virtual-key code a key pressed in {@link VmScreen} sends to the guest. JavaFX numbers its keys as
 * AWT does ({@code KeyCode.getCode()}); letters, digits, F-keys, the numpad, arrows and most modifiers already
 * have the Windows value, and the rest are here. Pure.
 */
final class VmKeys {

    private VmKeys() {}

    /** AWT key codes whose Windows virtual key differs. */
    private static final Map<Integer, Integer> DIFFERENT = Map.ofEntries(
            Map.entry(10, 0x0D),   // enter
            Map.entry(127, 0x2E),  // delete
            Map.entry(155, 0x2D),  // insert
            Map.entry(154, 0x2C),  // print screen
            Map.entry(524, 0x5B),  // Windows
            Map.entry(525, 0x5D),  // context menu
            Map.entry(59, 0xBA),   // ;
            Map.entry(61, 0xBB),   // =
            Map.entry(44, 0xBC),   // ,
            Map.entry(45, 0xBD),   // -
            Map.entry(46, 0xBE),   // .
            Map.entry(47, 0xBF),   // /
            Map.entry(91, 0xDB),   // [
            Map.entry(92, 0xDC),   // backslash
            Map.entry(93, 0xDD));  // ]

    /** The Windows virtual key for an AWT/JavaFX key code; codes with no difference pass through. */
    static int virtualKey(int awtCode) {
        return DIFFERENT.getOrDefault(awtCode, awtCode);
    }
}
