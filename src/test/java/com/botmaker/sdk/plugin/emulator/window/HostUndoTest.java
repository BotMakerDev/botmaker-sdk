package com.botmaker.sdk.plugin.emulator.window;

import com.botmaker.plugin.api.History;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** What ↶ and ↷ say over this PC's history. */
class HostUndoTest {

    private static History at(boolean undo, boolean redo) {
        return new History() {
            public boolean canUndo() { return undo; }
            public boolean canRedo() { return redo; }
            public String undoLabel() { return undo ? "Set GREETING" : ""; }
            public String redoLabel() { return redo ? "Forget an instance" : ""; }
            public void undo() {}
            public void redo() {}
            public AutoCloseable onChanged(Runnable listener) { return () -> {}; }
            public void batch(String label, Runnable writes) { writes.run(); }
        };
    }

    @Test
    void eachButtonNamesItsStepOrIsOff() {
        History both = at(true, true);
        assertEquals(new HostUndo.Face("↶ Undo", "Take back: Set GREETING", true), HostUndo.Face.undo(both));
        assertEquals(new HostUndo.Face("↷ Redo", "Do again: Forget an instance", true), HostUndo.Face.redo(both));
        assertEquals(new HostUndo.Face("↶ Undo", "Nothing to take back", false), HostUndo.Face.undo(History.NONE));
        assertEquals(new HostUndo.Face("↷ Redo", "Nothing to do again", false), HostUndo.Face.redo(at(true, false)));
    }

    @Test
    void undoStopsAtAStepThatFollowedTheEmulator() {
        History renamed = new History() {
            public boolean canUndo() { return true; }
            public boolean canRedo() { return false; }
            public String undoLabel() { return "Rename an instance"; }
            public String redoLabel() { return ""; }
            public void undo() {}
            public void redo() {}
            public AutoCloseable onChanged(Runnable listener) { return () -> {}; }
            public void batch(String label, Runnable writes) { writes.run(); }
        };
        HostUndo.Face face = HostUndo.Face.undo(renamed);
        assertEquals(false, face.enabled(), face.tip());
        assertEquals(true, face.tip().contains("followed the emulator"), face.tip());
    }
}
