package com.botmaker.sdk.plugin.emulator.window;

import javafx.scene.Scene;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

import java.util.Map;

/**
 * The keys the Emulators and Accounts windows answer (2026-11-14): F5 or Ctrl+R reads again, Ctrl+Z and Ctrl+Y (or
 * Ctrl+Shift+Z) undo and redo this PC's last change, Ctrl+F goes to the filter. Heard on the scene after its nodes, so
 * a text field keeps its own undo and a key a control used is not heard twice.
 */
public final class WindowKeys {

    /** What a key asks for. */
    public enum Action { REFRESH, UNDO, REDO, FIND, NONE }

    private WindowKeys() {}

    /** The action {@code key} asks for; NONE for any other key. Ctrl is the shortcut key, Cmd on a Mac. */
    public static Action of(KeyEvent key) {
        if (key.getCode() == KeyCode.F5 && !key.isShortcutDown()) return Action.REFRESH;
        if (!key.isShortcutDown() || key.isAltDown()) return Action.NONE;
        return switch (key.getCode()) {
            case R -> key.isShiftDown() ? Action.NONE : Action.REFRESH;
            case Z -> key.isShiftDown() ? Action.REDO : Action.UNDO;
            case Y -> key.isShiftDown() ? Action.NONE : Action.REDO;
            case F -> key.isShiftDown() ? Action.NONE : Action.FIND;
            default -> Action.NONE;
        };
    }

    /**
     * Has {@code scene} run each action's work, when given one; an undo or redo is left to a text field that has the
     * focus, for its own text.
     */
    public static void install(Scene scene, Map<Action, Runnable> work) {
        scene.addEventHandler(KeyEvent.KEY_PRESSED, key -> {
            Action action = of(key);
            Runnable run = work.get(action);
            if (run == null) return;
            boolean typing = scene.getFocusOwner() instanceof TextInputControl;
            if (typing && (action == Action.UNDO || action == Action.REDO)) return;
            run.run();
            key.consume();
        });
    }
}
