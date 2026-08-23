package com.botmaker.sdk.api.capture;

import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.util.Debug;
import com.botmaker.shared.capture.GenericWindow;
import com.botmaker.shared.capture.NativeController;
import com.botmaker.shared.capture.NativeControllerFactory;
import com.botmaker.shared.capture.WindowMatch;

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
        Debug.log("[Window] foreground -> " + (gw == null ? "none" : gw.getTitle()));
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
     * The window {@code titleSubstring} <em>most</em> refers to (case-insensitive), or empty if none
     * contains it. Ranking is delegated to shared {@link WindowMatch} so the bot runtime and Studio's
     * pilot pick the same window: an exact/prefix/whole-word hit beats an incidental substring hit (a
     * wiki tab or launcher entry named after the game), and dynamic title suffixes (score, level,
     * document name, …) are tolerated — while the shortest, largest matching window wins ties.
     */
    public static Optional<Window> find(String titleSubstring) {
        if (titleSubstring == null) {
            return Optional.empty();
        }
        GenericWindow gw = WindowMatch.best(controller().getAllWindows(), titleSubstring);
        Optional<Window> hit = gw == null ? Optional.empty() : Optional.of(new Window(gw));
        Debug.log("[Window] find \"" + titleSubstring + "\" -> "
                + hit.map(Window::title).orElse("no match"));
        return hit;
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
        Debug.log("[Window] click " + relativeX + "," + relativeY + " in " + title());
        controller().postLeftClick(handle, relativeX, relativeY);
    }

    /** Bring this window to the foreground and give it input focus. */
    public void focus() {
        Debug.log("[Window] focus " + title());
        controller().focusWindow(handle);
    }

    /** Move this window's top-left corner to the given absolute screen coordinate. */
    public void move(int x, int y) {
        Debug.log("[Window] move " + title() + " -> " + x + "," + y);
        controller().moveWindow(handle, x, y);
    }

    /**
     * Resize this window. Useful to force a game into the exact resolution its templates were
     * cropped at, since template matching breaks when the window is a different size.
     */
    public void resize(int width, int height) {
        Debug.log("[Window] resize " + title() + " -> " + width + "x" + height);
        controller().resizeWindow(handle, width, height);
    }

    @Override
    public String toString() {
        return "Window[title=" + title() + ", bounds=" + bounds() + "]";
    }
}
