package com.botmaker.sdk.plugin.editors;

import com.botmaker.sdk.api.input.Key;
import javafx.scene.input.KeyCode;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * The key a JavaFX {@link KeyCode} is, as the SDK names it — how a real keystroke selects its cap. Most share a
 * name; the rest are listed. JavaFX reports both Enters as {@code ENTER}, so {@link Key#NUMPAD_ENTER} is reached
 * only by clicking its cap.
 */
final class KeyCodes {

    private static final Map<KeyCode, Key> RENAMED = renamed();

    private KeyCodes() {}

    static Optional<Key> toKey(KeyCode code) {
        if (code == null) return Optional.empty();
        Key renamed = RENAMED.get(code);
        if (renamed != null) return Optional.of(renamed);
        try {
            return Optional.of(Key.valueOf(code.name()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static Map<KeyCode, Key> renamed() {
        Map<KeyCode, Key> m = new EnumMap<>(KeyCode.class);
        for (int i = 0; i < 10; i++) {
            m.put(KeyCode.valueOf("DIGIT" + i), Key.valueOf("NUM" + i));
            m.put(KeyCode.valueOf("NUMPAD" + i), Key.valueOf("NUMPAD_" + i));
        }
        m.put(KeyCode.CONTROL, Key.CTRL);
        m.put(KeyCode.WINDOWS, Key.META);
        m.put(KeyCode.COMMAND, Key.META);
        m.put(KeyCode.BACK_SPACE, Key.BACKSPACE);
        m.put(KeyCode.BACK_QUOTE, Key.BACKQUOTE);
        m.put(KeyCode.OPEN_BRACKET, Key.LEFT_BRACKET);
        m.put(KeyCode.CLOSE_BRACKET, Key.RIGHT_BRACKET);
        m.put(KeyCode.BACK_SLASH, Key.BACKSLASH);
        m.put(KeyCode.CAPS, Key.CAPS_LOCK);
        m.put(KeyCode.ADD, Key.NUMPAD_ADD);
        m.put(KeyCode.SUBTRACT, Key.NUMPAD_SUBTRACT);
        m.put(KeyCode.MULTIPLY, Key.NUMPAD_MULTIPLY);
        m.put(KeyCode.DIVIDE, Key.NUMPAD_DIVIDE);
        m.put(KeyCode.DECIMAL, Key.NUMPAD_DECIMAL);
        // The keypad's arrows with Num Lock off send the arrows a bot presses.
        m.put(KeyCode.KP_UP, Key.UP);
        m.put(KeyCode.KP_DOWN, Key.DOWN);
        m.put(KeyCode.KP_LEFT, Key.LEFT);
        m.put(KeyCode.KP_RIGHT, Key.RIGHT);
        return m;
    }
}
