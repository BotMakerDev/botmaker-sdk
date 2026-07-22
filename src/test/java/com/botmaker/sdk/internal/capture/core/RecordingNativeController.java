package com.botmaker.sdk.internal.capture.core;

import com.botmaker.shared.capture.GenericWindow;
import com.botmaker.shared.capture.NativeController;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Test double for {@link NativeController} that records every call as a readable event string, so
 * input/window operations can be asserted without a live X11/Windows session. Enumeration returns a
 * single configurable window.
 */
public class RecordingNativeController implements NativeController {

    public final List<String> events = new ArrayList<>();
    /** Targeted (window-routed) key events, kept separate so window-resolution noise doesn't pollute asserts. */
    public final List<String> windowInput = new ArrayList<>();
    public GenericWindow window =
            new GenericWindow("h", "Test Game Window", new Rectangle(100, 50, 800, 600));

    @Override public GenericWindow getForegroundWindow() { events.add("getForegroundWindow"); return window; }
    @Override public List<GenericWindow> getChildWindows(GenericWindow parent) { return List.of(); }
    @Override public List<GenericWindow> getAllWindows() { events.add("getAllWindows"); return List.of(window); }

    @Override public BufferedImage captureWindow(GenericWindow w) {
        events.add("captureWindow");
        return new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
    }

    @Override public void postLeftClick(GenericWindow w, int relativeX, int relativeY) {
        events.add("postLeftClick(" + relativeX + "," + relativeY + ")");
    }
    @Override public void postLeftClickScreen(int xAbs, int yAbs) { events.add("postLeftClickScreen(" + xAbs + "," + yAbs + ")"); }

    @Override public void focusWindow(GenericWindow w) { events.add("focusWindow"); }
    @Override public void moveWindow(GenericWindow w, int x, int y) { events.add("moveWindow(" + x + "," + y + ")"); }
    @Override public void resizeWindow(GenericWindow w, int width, int height) { events.add("resizeWindow(" + width + "," + height + ")"); }

    @Override public void keyDown(int nativeKeyCode) { events.add("keyDown(" + nativeKeyCode + ")"); }
    @Override public void keyUp(int nativeKeyCode) { events.add("keyUp(" + nativeKeyCode + ")"); }
    @Override public void typeText(String text) { events.add("typeText(" + text + ")"); }

    @Override public void keyDown(GenericWindow w, int nativeKeyCode) {
        windowInput.add("keyDown(" + title(w) + "," + nativeKeyCode + ")");
    }
    @Override public void keyUp(GenericWindow w, int nativeKeyCode) {
        windowInput.add("keyUp(" + title(w) + "," + nativeKeyCode + ")");
    }
    @Override public void typeText(GenericWindow w, String text) {
        windowInput.add("typeText(" + title(w) + "," + text + ")");
    }
    private static String title(GenericWindow w) { return w == null ? "null" : w.getTitle(); }
    @Override public void mouseMove(int xAbs, int yAbs) { events.add("mouseMove(" + xAbs + "," + yAbs + ")"); }
    @Override public void mouseButton(int button, boolean press) { events.add("mouseButton(" + button + "," + press + ")"); }
    @Override public void scroll(int amount) { events.add("scroll(" + amount + ")"); }
}
