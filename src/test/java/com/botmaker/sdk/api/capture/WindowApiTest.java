package com.botmaker.sdk.api.capture;

import com.botmaker.sdk.api.Point;
import com.botmaker.sdk.api.Rect;
import com.botmaker.shared.capture.NativeControllerFactory;
import com.botmaker.sdk.internal.capture.core.RecordingNativeController;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class WindowApiTest {

    private RecordingNativeController fake;

    @BeforeEach
    void setUp() {
        fake = new RecordingNativeController();
        NativeControllerFactory.setForTesting(fake);
    }

    @AfterEach
    void tearDown() {
        NativeControllerFactory.setForTesting(null);
    }

    @Test
    void foregroundExposesBoundsAndOrigin() {
        Window w = Window.foreground().orElseThrow();
        assertEquals("Test Game Window", w.title());

        Rect bounds = w.bounds();
        assertEquals(100, bounds.x());
        assertEquals(50, bounds.y());
        assertEquals(800, w.width());
        assertEquals(600, w.height());

        // origin() is the window's absolute top-left — what a matcher adds to in-image coords.
        Point origin = w.origin();
        assertEquals(100, origin.x());
        assertEquals(50, origin.y());
    }

    @Test
    void findMatchesTitleSubstringCaseInsensitively() {
        assertTrue(Window.find("game").isPresent());
        assertTrue(Window.find("TEST").isPresent());
        assertEquals(Optional.empty(), Window.find("no-such-window"));
    }

    @Test
    void windowIsACaptureSource() {
        Window w = Window.foreground().orElseThrow();
        assertTrue(w instanceof CaptureSource);
        assertNotNull(w.capture());
        assertTrue(fake.events.contains("captureWindow"));
    }

    @Test
    void managementDelegatesToController() {
        Window w = Window.foreground().orElseThrow();
        w.focus();
        w.move(10, 20);
        w.resize(1280, 720);
        w.click(5, 6);

        assertTrue(fake.events.contains("focusWindow"));
        assertTrue(fake.events.contains("moveWindow(10,20)"));
        assertTrue(fake.events.contains("resizeWindow(1280,720)"));
        assertTrue(fake.events.contains("postLeftClick(5,6)"));
    }
}
