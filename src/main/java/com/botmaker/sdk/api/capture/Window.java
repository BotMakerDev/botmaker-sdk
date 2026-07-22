package com.botmaker.sdk.api.capture;

import com.botmaker.sdk.api.Point;
import com.botmaker.sdk.api.Rect;
import com.botmaker.shared.capture.GenericWindow;
import com.botmaker.shared.capture.NativeController;
import com.botmaker.shared.capture.NativeControllerFactory;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A specific application window the bot can target — capture it, click inside it, and manage it
 * (focus / move / resize). Because {@code Window} is a {@link CaptureSource}, it can be passed
 * straight into the vision layer (e.g. {@code ImageFinder.find(template, window)}) so templates are
 * matched and clicked in the window's own
 * coordinate space — surviving window moves, focus changes and multi-monitor layouts.
 *
 * <p>The underlying native handle is kept opaque; obtain a {@code Window} via the static factories.
 */
public class Window implements CaptureSource {

    private final GenericWindow handle;

    Window(GenericWindow handle) {
        this.handle = handle;
    }

    private static NativeController controller() {
        return NativeControllerFactory.get();
    }

    // --- Factories ---

    /** The window that currently has focus, or empty if none could be resolved. */
    public static Optional<Window> foreground() {
        GenericWindow gw = controller().getForegroundWindow();
        return gw == null ? Optional.empty() : Optional.of(new Window(gw));
    }

    /** Every top-level window the window manager reports (titled + viewable). */
    public static List<Window> all() {
        List<Window> windows = new ArrayList<>();
        for (GenericWindow gw : controller().getAllWindows()) {
            if (gw != null) {
                windows.add(new Window(gw));
            }
        }
        return windows;
    }

    /**
     * The first window whose title contains {@code titleSubstring} (case-insensitive), or empty if
     * none match. Matching against a substring is deliberately lenient so bots survive dynamic
     * title suffixes (score, level, document name, …).
     */
    public static Optional<Window> find(String titleSubstring) {
        if (titleSubstring == null) {
            return Optional.empty();
        }
        String needle = titleSubstring.toLowerCase();
        return all().stream()
                .filter(w -> w.title().toLowerCase().contains(needle))
                .findFirst();
    }

    // --- CaptureSource ---

    @Override
    public BufferedImage capture() {
        return controller().captureWindow(handle);
    }

    @Override
    public Point origin() {
        Rectangle r = handle.getRect();
        return new Point(r.x, r.y);
    }

    @Override
    public GenericWindow targetWindow() {
        return handle;
    }

    // --- Accessors ---

    public String title() {
        String t = handle.getTitle();
        return t == null ? "" : t;
    }

    /** Absolute screen bounds of the window. */
    public Rect bounds() {
        Rectangle r = handle.getRect();
        return new Rect(r.x, r.y, r.width, r.height);
    }

    public int width() {
        return handle.getRect().width;
    }

    public int height() {
        return handle.getRect().height;
    }

    // --- Interaction ---

    /** Click at coordinates relative to this window's top-left (converted to absolute internally). */
    public void click(int relativeX, int relativeY) {
        controller().postLeftClick(handle, relativeX, relativeY);
    }

    /** Bring this window to the foreground and give it input focus. */
    public void focus() {
        controller().focusWindow(handle);
    }

    /** Move this window's top-left corner to the given absolute screen coordinate. */
    public void move(int x, int y) {
        controller().moveWindow(handle, x, y);
    }

    /**
     * Resize this window. Useful to force a game into the exact resolution its templates were
     * cropped at, since template matching breaks when the window is a different size.
     */
    public void resize(int width, int height) {
        controller().resizeWindow(handle, width, height);
    }

    @Override
    public String toString() {
        return "Window[title=" + title() + ", bounds=" + bounds() + "]";
    }
}
