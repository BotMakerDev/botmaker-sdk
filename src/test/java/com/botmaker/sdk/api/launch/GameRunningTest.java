package com.botmaker.sdk.api.launch;

import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.shared.capture.GenericWindow;
import com.botmaker.shared.capture.NativeController;
import com.botmaker.shared.capture.NativeControllerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests {@link Game}'s window-based running detection (via {@link CaptureSource#window}) against a fake
 * {@link NativeController} injected via {@link NativeControllerFactory#setForTesting}, so nothing real is
 * launched or enumerated.
 */
class GameRunningTest {

    @AfterEach
    void reset() {
        NativeControllerFactory.setForTesting(null);
    }

    private static GenericWindow window(String title) {
        return new GenericWindow(new Object(), title, new Rectangle(0, 0, 100, 100));
    }

    @Test
    void isRunningMatchesTitleSubstringCaseInsensitively() {
        NativeControllerFactory.setForTesting(new FakeController(List.of(window("My Cool Game — Level 1"))));
        assertTrue(Game.isRunning(CaptureSource.window("cool game")));
        assertTrue(Game.isRunning(CaptureSource.window("Level 1")));
        assertFalse(Game.isRunning(CaptureSource.window("Some Other App")));
    }

    @Test
    void isRunningFalseWhenNoWindows() {
        NativeControllerFactory.setForTesting(new FakeController(List.of()));
        assertFalse(Game.isRunning(CaptureSource.window("anything")));
    }

    @Test
    void waitForLaunchReturnsFalseOnTimeoutWhenAbsent() {
        NativeControllerFactory.setForTesting(new FakeController(List.of(window("Unrelated"))));
        assertFalse(Game.waitForLaunch(CaptureSource.window("MissingGame"), 200));
    }

    @Test
    void waitForLaunchReturnsTrueImmediatelyWhenPresent() {
        NativeControllerFactory.setForTesting(new FakeController(List.of(window("Present Game"))));
        assertTrue(Game.waitForLaunch(CaptureSource.window("Present Game"), 200));
    }

    @Test
    void launchIfNotRunningSkipsWhenAlreadyRunning() {
        NativeControllerFactory.setForTesting(new FakeController(List.of(window("Already Here"))));
        // Returns false (not launched) without ever touching the executable path, so a bogus path is fine.
        assertFalse(Game.launchIfNotRunning("/nonexistent/should-not-run", CaptureSource.window("Already Here")));
    }

    /** Minimal {@link NativeController} that only answers {@code getAllWindows}; everything else is a no-op. */
    private record FakeController(List<GenericWindow> windows) implements NativeController {
        @Override public List<GenericWindow> getAllWindows() { return windows; }
        @Override public GenericWindow getForegroundWindow() { return null; }
        @Override public List<GenericWindow> getChildWindows(GenericWindow parent) { return List.of(); }
        @Override public BufferedImage captureWindow(GenericWindow window) { return null; }
        @Override public void postLeftClick(GenericWindow window, int relativeX, int relativeY) {}
        @Override public void postLeftClickScreen(int xAbs, int yAbs) {}
        @Override public void focusWindow(GenericWindow window) {}
        @Override public void moveWindow(GenericWindow window, int x, int y) {}
        @Override public void resizeWindow(GenericWindow window, int width, int height) {}
        @Override public void keyDown(int nativeKeyCode) {}
        @Override public void keyUp(int nativeKeyCode) {}
        @Override public void typeText(String text) {}
        @Override public void mouseMove(int xAbs, int yAbs) {}
        @Override public void mouseButton(int button, boolean press) {}
        @Override public void scroll(int amount) {}
    }
}
