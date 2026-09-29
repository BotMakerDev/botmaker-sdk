package com.botmaker.sdk.plugin.pilot;

import com.botmaker.sdk.api.interaction.Key;

import java.util.EnumMap;
import java.util.Map;

/**
 * The Android {@code KEYCODE_*} a pilot key press becomes on the emulator route ({@code input keyevent}).
 *
 * <p>Only the keys the pilot's special-key row offers, plus the letters and digits, which Android numbers in
 * order. Anything else answers {@code -1} and the phone is told the screen does not take it, rather than a
 * guess pressing some other key.
 */
final class AndroidKeys {

    private static final Map<Key, Integer> CODES = new EnumMap<>(Key.class);

    static {
        CODES.put(Key.ENTER, 66);
        CODES.put(Key.NUMPAD_ENTER, 66);
        CODES.put(Key.ESCAPE, 111);
        CODES.put(Key.TAB, 61);
        CODES.put(Key.SPACE, 62);
        CODES.put(Key.BACKSPACE, 67);   // KEYCODE_DEL is Android's backspace
        CODES.put(Key.DELETE, 112);     // KEYCODE_FORWARD_DEL
        CODES.put(Key.UP, 19);
        CODES.put(Key.DOWN, 20);
        CODES.put(Key.LEFT, 21);
        CODES.put(Key.RIGHT, 22);
        CODES.put(Key.HOME, 122);       // KEYCODE_MOVE_HOME
        CODES.put(Key.END, 123);        // KEYCODE_MOVE_END
        CODES.put(Key.PAGE_UP, 92);
        CODES.put(Key.PAGE_DOWN, 93);
        Key[] letters = {Key.A, Key.B, Key.C, Key.D, Key.E, Key.F, Key.G, Key.H, Key.I, Key.J, Key.K, Key.L,
                Key.M, Key.N, Key.O, Key.P, Key.Q, Key.R, Key.S, Key.T, Key.U, Key.V, Key.W, Key.X, Key.Y, Key.Z};
        for (int i = 0; i < letters.length; i++) CODES.put(letters[i], 29 + i);   // KEYCODE_A = 29
        Key[] digits = {Key.NUM0, Key.NUM1, Key.NUM2, Key.NUM3, Key.NUM4, Key.NUM5, Key.NUM6, Key.NUM7,
                Key.NUM8, Key.NUM9};
        for (int i = 0; i < digits.length; i++) CODES.put(digits[i], 7 + i);      // KEYCODE_0 = 7
    }

    private AndroidKeys() {
    }

    /** The Android key code for {@code key}, or {@code -1} when the emulator route does not press it. */
    static int code(Key key) {
        return CODES.getOrDefault(key, -1);
    }
}
