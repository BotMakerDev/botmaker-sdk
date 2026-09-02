package com.botmaker.sdk.internal.plugin.record;

import com.botmaker.sdk.internal.plugin.record.MacroTranslator.Macro;
import com.botmaker.sdk.internal.plugin.record.MacroTranslator.WindowRef;
import com.botmaker.shared.input.InputEvent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies {@link MacroTranslator} — the pure mapping from a recorded {@link InputEvent} stream to Java
 * statements. All coordinates are window-relative; the window is at origin (100,100), size 800x600.
 *
 * <p>It asserted on Studio's {@code BlockType} tree until 2026-09-02, which is why the assertions read as
 * source text now: a plugin's translator emits Java, because Java source is the wire every contract surface
 * already uses and a host's block model is not something a plugin may build.
 */
class MacroTranslatorTest {

    private static final WindowRef WINDOW = new WindowRef("Game", 100, 100, 800, 600);

    private static List<String> lines(List<InputEvent> events) {
        return MacroTranslator.translate(events, WINDOW).statements();
    }

    @Test
    void leftClickInsideWindowBecomesWindowRelativeClick() {
        List<InputEvent> events = List.of(
                new InputEvent.ButtonPress(1, 150, 220, 1000),
                new InputEvent.ButtonRelease(1, 150, 220, 1040));

        assertEquals(List.of("Mouse.click(CaptureSource.window(\"Game\"), 50, 120);"), lines(events));
    }

    @Test
    void theImportsAreTheTypesTheStatementsActuallyName() {
        Macro macro = MacroTranslator.translate(List.of(
                new InputEvent.ButtonPress(1, 150, 220, 1000),
                new InputEvent.ButtonRelease(1, 150, 220, 1040)), WINDOW);

        assertEquals(List.of("com.botmaker.sdk.api.interaction.Mouse",
                "com.botmaker.sdk.api.capture.CaptureSource"), macro.imports());
        assertTrue(macro.java().startsWith("import com.botmaker.sdk.api.interaction.Mouse;\n"), macro.java());
        assertTrue(macro.java().endsWith("Mouse.click(CaptureSource.window(\"Game\"), 50, 120);\n"), macro.java());
    }

    @Test
    void printableKeyBurstCoalescesToOneTypeCall() {
        List<InputEvent> events = List.of(
                new InputEvent.KeyPress(0, 'h', 1000),
                new InputEvent.KeyPress(0, 'i', 1010),
                new InputEvent.KeyPress(0, '!', 1020));

        assertEquals(List.of("Keyboard.type(\"hi!\");"), lines(events));
    }

    @Test
    void typedTextIsEscapedAsAJavaStringLiteral() {
        // The one thing a hand-rolled emitter gets wrong, and the reason the toolkit's Source does the quoting:
        // a user typing a quote or a backslash into the game must not produce source that will not compile.
        List<InputEvent> events = List.of(
                new InputEvent.KeyPress(0, '"', 1000),
                new InputEvent.KeyPress(0, '\\', 1010));

        assertEquals(List.of("Keyboard.type(\"\\\"\\\\\");"), lines(events));
    }

    @Test
    void backspaceEditsTheTypingBuffer() {
        List<InputEvent> events = List.of(
                new InputEvent.KeyPress(0, 'a', 1000),
                new InputEvent.KeyPress(0, 'b', 1010),
                new InputEvent.KeyPress(0, 0xFF08L, 1020), // BackSpace
                new InputEvent.KeyPress(0, 'c', 1030));

        assertEquals(List.of("Keyboard.type(\"ac\");"), lines(events));
    }

    @Test
    void namedKeyBecomesKeyboardTap() {
        List<InputEvent> events = List.of(new InputEvent.KeyPress(0, 0xFF0DL, 1000)); // Return

        assertEquals(List.of("Keyboard.tap(Key.ENTER);"), lines(events));
    }

    @Test
    void idleGapInsertsAWaitBetweenGestures() {
        List<InputEvent> events = List.of(
                new InputEvent.ButtonPress(1, 150, 150, 1000),
                new InputEvent.ButtonRelease(1, 150, 150, 1040),
                new InputEvent.ButtonPress(1, 160, 160, 2100),
                new InputEvent.ButtonRelease(1, 160, 160, 2140));

        // gap = 2140 - 1040 = 1100, rounded to nearest 100 = 1100
        assertEquals(List.of("Mouse.click(CaptureSource.window(\"Game\"), 50, 50);",
                "Wait.milliseconds(1100);",
                "Mouse.click(CaptureSource.window(\"Game\"), 60, 60);"), lines(events));
    }

    @Test
    void clickOutsideWindowIsDropped() {
        List<InputEvent> events = List.of(
                new InputEvent.ButtonPress(1, 10, 10, 1000),   // above/left of the window origin (100,100)
                new InputEvent.ButtonRelease(1, 10, 10, 1040));

        assertTrue(MacroTranslator.translate(events, WINDOW).isEmpty());
    }

    @Test
    void leftDragIsSuppressedNotEmittedAsClick() {
        List<InputEvent> events = new ArrayList<>(List.of(
                new InputEvent.ButtonPress(1, 150, 150, 1000),
                new InputEvent.Motion(180, 190, 1020),
                new InputEvent.ButtonRelease(1, 180, 190, 1040)));

        assertTrue(MacroTranslator.translate(events, WINDOW).isEmpty());
    }
}
