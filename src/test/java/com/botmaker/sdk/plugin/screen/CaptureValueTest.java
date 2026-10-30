package com.botmaker.sdk.plugin.screen;

import com.botmaker.sdk.internal.capture.core.RecordingNativeController;
import com.botmaker.shared.capture.GenericWindow;
import org.junit.jupiter.api.Test;

import java.awt.Rectangle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaptureValueTest {

    @Test
    void aWindowIsFoundAmongTheControllerItIsAskedOf() {
        // A private display's windows are its own controller's, never the desktop's (2026-10-30, Balatro).
        RecordingNativeController display = new RecordingNativeController();
        display.window = new GenericWindow(1L, "Balatro", new Rectangle(0, 0, 1280, 720), 0, "steam_app_2379780");

        assertTrue(CaptureValue.windowTitled("balatro", display).isPresent(), "the title, ignoring case");
        assertEquals("Window[steam_app_2379780]", // a class of its own: found by it alone
                CaptureValue.windowTitled("Balatro", display).orElseThrow().toString());
        assertTrue(CaptureValue.windowTitled("Firestone", display).isEmpty(), "no such window there");
    }
}
