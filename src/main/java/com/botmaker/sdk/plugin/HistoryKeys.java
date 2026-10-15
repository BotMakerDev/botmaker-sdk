package com.botmaker.sdk.plugin;

import javafx.scene.Node;
import javafx.scene.control.ComboBoxBase;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;

import java.util.function.Consumer;

/**
 * Ctrl+Z, Ctrl+Y and Ctrl+Shift+Z in a plugin window whose ↶ and ↷ are Studio's history: 🗺 Screens and the
 * Activity Flow. A key pressed in a field the user types in is that field's own undo, and is left to it.
 */
public final class HistoryKeys {

    private HistoryKeys() {}

    private static final KeyCombination UNDO = new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN);
    private static final KeyCombination REDO = new KeyCodeCombination(KeyCode.Y, KeyCombination.SHORTCUT_DOWN);
    private static final KeyCombination REDO_SHIFT = new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN,
            KeyCombination.SHIFT_DOWN);

    /**
     * Hands {@code step} {@code true} for an undo key and {@code false} for a redo key, and consumes the key;
     * anything else, or a key in a text field, passes on untouched.
     */
    public static void handle(KeyEvent key, Consumer<Boolean> step) {
        for (Object at = key.getTarget(); at instanceof Node node; at = node.getParent()) {
            if (node instanceof TextInputControl || node instanceof ComboBoxBase<?> box && box.isEditable()) return;
        }
        if (UNDO.match(key)) step.accept(true);
        else if (REDO.match(key) || REDO_SHIFT.match(key)) step.accept(false);
        else return;
        key.consume();
    }
}
