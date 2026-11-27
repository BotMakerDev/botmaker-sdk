// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.internal.emulator;

import com.botmaker.sdk.api.input.Key;

import java.util.EnumMap;
import java.util.Map;
import java.util.OptionalInt;

/**
 * A keyboard {@link Key} as Android's key event, for an emulator that takes no desktop key: Escape is Android's Back,
 * Home its Home, as an emulator's own keyboard maps them; the rest are the keys of the same name.
 */
public final class AndroidKeys {

    /** Android's {@code KEYCODE_BACK}. */
    public static final int BACK = 4;
    /** Android's {@code KEYCODE_HOME}. */
    public static final int HOME = 3;

    private static final Map<Key, Integer> CODES = new EnumMap<>(Key.class);

    static {
        CODES.put(Key.ESCAPE, BACK);
        CODES.put(Key.HOME, HOME);
        CODES.put(Key.ENTER, EmulatorSource.KEYCODE_ENTER);
        CODES.put(Key.NUMPAD_ENTER, EmulatorSource.KEYCODE_ENTER);
        CODES.put(Key.TAB, 61);
        CODES.put(Key.SPACE, 62);
        CODES.put(Key.BACKSPACE, 67);
        CODES.put(Key.DELETE, 112);
        CODES.put(Key.UP, 19);
        CODES.put(Key.DOWN, 20);
        CODES.put(Key.LEFT, 21);
        CODES.put(Key.RIGHT, 22);
        CODES.put(Key.PAGE_UP, 92);
        CODES.put(Key.PAGE_DOWN, 93);
        CODES.put(Key.END, 123);
        Key[] letters = {Key.A, Key.B, Key.C, Key.D, Key.E, Key.F, Key.G, Key.H, Key.I, Key.J, Key.K, Key.L, Key.M,
                Key.N, Key.O, Key.P, Key.Q, Key.R, Key.S, Key.T, Key.U, Key.V, Key.W, Key.X, Key.Y, Key.Z};
        for (int i = 0; i < letters.length; i++) CODES.put(letters[i], 29 + i);
        Key[] digits = {Key.NUM0, Key.NUM1, Key.NUM2, Key.NUM3, Key.NUM4, Key.NUM5, Key.NUM6, Key.NUM7, Key.NUM8,
                Key.NUM9};
        for (int i = 0; i < digits.length; i++) CODES.put(digits[i], 7 + i);
    }

    private AndroidKeys() {}

    /** {@code key}'s Android key code; empty for a key Android has none for here (a modifier, a function key). */
    public static OptionalInt of(Key key) {
        Integer code = key == null ? null : CODES.get(key);
        return code == null ? OptionalInt.empty() : OptionalInt.of(code);
    }
}
