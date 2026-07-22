package com.botmaker.sdk.api.interaction;

import com.botmaker.sdk.api.Debug;
import com.botmaker.sdk.api.Point;
import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.shared.capture.NativeController;
import com.botmaker.shared.capture.NativeControllerFactory;

public class Mouse {

    private static NativeController controller() {
        return NativeControllerFactory.get();
    }

    /**
     * Left-clicks at specific coordinates on the screen. Uses the low-level no-cursor-move path
     * (PostMessage on Windows / XTest on Linux) for compatibility with existing behaviour.
     */
    public static void click(Point p) {
        if (p == null) return;
        Debug.log("[Mouse] click " + p);
        // Delegate to internal implementation, explicitly casting double coordinates to int
        controller().postLeftClickScreen((int) p.x, (int) p.y);
    }

    public static void click(int x, int y) {
        click(new Point(x, y));
    }

    /**
     * Left-clicks at {@code (x, y)} relative to a {@link CaptureSource}'s {@link CaptureSource#origin() origin}
     * — a fixed point inside a window (or a monitor / region), independent of where that surface currently
     * sits on the desktop. Equivalent to clicking {@code source.origin() + (x, y)} in absolute coordinates.
     */
    public static void click(CaptureSource source, int x, int y) {
        if (source == null) return;
        Point o = source.origin();
        Debug.log("[Mouse] click " + x + "," + y + " relative to " + o);
        click((int) o.x + x, (int) o.y + y);
    }

    // --- Movement ---

    /** Move the cursor to an absolute screen coordinate. */
    public static void move(Point p) {
        if (p == null) return;
        Debug.log("[Mouse] move " + p);
        controller().mouseMove((int) p.x, (int) p.y);
    }

    /** Move the cursor to absolute screen coordinates {@code (x, y)}. */
    public static void move(int x, int y) {
        Debug.log("[Mouse] move " + x + "," + y);
        controller().mouseMove(x, y);
    }

    // --- Button actions ---

    /** Hold a mouse button down at the current cursor position. */
    public static void down(MouseButton button) {
        Debug.log("[Mouse] " + button + " down");
        controller().mouseButton(button.code(), true);
    }

    /** Move to {@code p}, then hold {@code button} down there. */
    public static void down(MouseButton button, Point p) {
        if (p != null) move(p);
        down(button);
    }

    /** Release a held mouse button. */
    public static void up(MouseButton button) {
        Debug.log("[Mouse] " + button + " up");
        controller().mouseButton(button.code(), false);
    }

    private static void clickButtonAt(Point p, MouseButton button) {
        if (p != null) move(p);
        down(button);
        up(button);
    }

    public static void rightClick(Point p) {
        Debug.log("[Mouse] rightClick " + p);
        clickButtonAt(p, MouseButton.RIGHT);
    }

    public static void middleClick(Point p) {
        Debug.log("[Mouse] middleClick " + p);
        clickButtonAt(p, MouseButton.MIDDLE);
    }

    /** Two quick left presses at the given point. */
    public static void doubleClick(Point p) {
        Debug.log("[Mouse] doubleClick " + p);
        if (p != null) move(p);
        down(MouseButton.LEFT);
        up(MouseButton.LEFT);
        down(MouseButton.LEFT);
        up(MouseButton.LEFT);
    }

    /** Press-and-hold at {@code start}, move straight to {@code end}, then release (an instant drag). */
    public static void drag(Point start, Point end) {
        drag(start, end, 0);
    }

    /**
     * Press-and-hold at {@code start}, glide to {@code end} over {@code durationMs} milliseconds, then
     * release. A non-zero duration interpolates the move in small steps so games that track drag
     * velocity (map panning, slingshots, drawing) see a smooth gesture instead of a teleport.
     */
    public static void drag(Point start, Point end, long durationMs) {
        Debug.log("[Mouse] drag " + start + " -> " + end + " over " + durationMs + "ms");
        move(start);
        down(MouseButton.LEFT);
        if (durationMs > 0 && start != null && end != null) {
            int steps = Math.max(1, (int) (durationMs / 15));
            long perStep = durationMs / steps;
            for (int i = 1; i <= steps; i++) {
                double t = (double) i / steps;
                move(new Point(start.x + (end.x - start.x) * t, start.y + (end.y - start.y) * t));
                if (perStep > 0) {
                    try {
                        Thread.sleep(perStep);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        } else {
            move(end);
        }
        up(MouseButton.LEFT);
    }

    /**
     * Scroll the wheel by {@code notches}. Positive scrolls up / away from you, negative scrolls
     * down / toward you. Prefer the clearer {@link #scrollUp(int)} / {@link #scrollDown(int)}.
     */
    public static void scroll(int notches) {
        Debug.log("[Mouse] scroll " + notches);
        controller().scroll(notches);
    }

    /** Scroll up / away from you by {@code notches} (always a positive amount). */
    public static void scrollUp(int notches) {
        Debug.log("[Mouse] scrollUp " + notches);
        controller().scroll(Math.abs(notches));
    }

    /** Scroll down / toward you by {@code notches} (always a positive amount). */
    public static void scrollDown(int notches) {
        Debug.log("[Mouse] scrollDown " + notches);
        controller().scroll(-Math.abs(notches));
    }
}