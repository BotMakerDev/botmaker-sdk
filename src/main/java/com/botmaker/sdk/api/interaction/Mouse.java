package com.botmaker.sdk.api.interaction;

import com.botmaker.plugin.api.palette.Facade;
import com.botmaker.plugin.api.meta.Internal;
import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.util.Debug;
import com.botmaker.sdk.internal.observe.Bots;
import com.botmaker.sdk.internal.observe.Surface;
import com.botmaker.sdk.internal.observe.SwipeEvent;
import com.botmaker.shared.capture.NativeController;
import com.botmaker.shared.capture.NativeControllerFactory;
import com.botmaker.session.ActiveSession;
import com.botmaker.session.DesktopSession;
import com.botmaker.session.PointerPolicy;

/**
 * Real pointer input — clicks, moves, drags and the wheel.
 *
 * <p><b>Curated for the palette</b> (see {@code @Palette}): fifteen of the sixteen are offered. The one hidden is
 * {@link #scroll(int)}, and the reason was already written down before this sweep existed — its own javadoc ends
 * <em>"Prefer the clearer {@link #scrollUp(int)} / {@link #scrollDown(int)}"</em>. Until now the menu offered all
 * three as equals and quietly contradicted that sentence; the annotation is what makes the author's preference
 * the thing the editor actually proposes. A signed {@code notches} is exactly the ambiguity the named pair
 * removes, and hiding it costs nothing, since it stays public for the bot that computes its direction.
 *
 * <p>Nothing else here is a duplicate spelling. {@code click(Point)} and {@code click(int, int)} differ in what
 * the user is holding — a {@link Point} out of a match, or two literals — and the {@code CaptureSource} form
 * asks a genuinely different question (coordinates within a source, not on the screen). {@code drag}'s
 * {@code durationMs} has no property home, unlike {@code ImageClicker}'s {@code delayMs}, so both drag shapes
 * stay.
 */
@Facade(category = "interaction", categoryLabel = "Interaction", icon = "🖱", order = 10)
public class Mouse {

    /**
     * The controller every method below drives through. When an {@link ActiveSession} is registered (an
     * isolated bot on its private {@code :N} display) this is the session's {@code :N}-bound controller, so
     * the same click/move/type code targets the nested display; otherwise it is the process-wide {@code :0}
     * singleton — today's behaviour, unchanged whenever no session is active.
     */
    private static NativeController controller() {
        DesktopSession session = session();
        return session != null ? session.controller() : NativeControllerFactory.get();
    }

    /**
     * The session every gesture below asks about before deciding whether to hand the cursor back — see
     * {@link PointerPolicy}. {@code null} means the user's own desktop, where the courtesy warp is the point.
     */
    private static DesktopSession session() {
        return ActiveSession.get();
    }

    /**
     * Left-clicks at absolute screen coordinates. On the user's desktop the cursor is put back where it started;
     * in a private session it stays on the target — see {@link PointerPolicy}, and note that warping away there
     * is what made a game render a hover highlight instead of registering the click.
     *
     * <p>This drives real pointer input rather than posting a synthetic event to the window under the point.
     * Synthetic events are silently dropped by games (and by anything else reading raw input), which is why
     * clicks used to do nothing in-game; the cost is that the pointer visibly moves (and, on {@code :0}, comes
     * back), and that the click lands on whatever is <em>topmost</em> at that coordinate.
     */
    public static void click(Point p) {
        if (p == null) return;
        Debug.log("[Mouse] click " + p);
        PointerPolicy.click(controller(), session(), p.x(), p.y(), MouseButton.LEFT.code());
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
        click(o.x() + x, o.y() + y);
    }

    // --- Movement ---

    /** Move the cursor to an absolute screen coordinate. */
    public static void move(Point p) {
        if (p == null) return;
        Debug.log("[Mouse] move " + p);
        controller().mouseMove(p.x(), p.y());
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

    public static void rightClick(Point p) {
        if (p == null) return;
        Debug.log("[Mouse] rightClick " + p);
        PointerPolicy.click(controller(), session(), p.x(), p.y(), MouseButton.RIGHT.code());
    }

    public static void middleClick(Point p) {
        if (p == null) return;
        Debug.log("[Mouse] middleClick " + p);
        PointerPolicy.click(controller(), session(), p.x(), p.y(), MouseButton.MIDDLE.code());
    }

    /**
     * Two quick left presses at the given point, with the cursor put back afterwards. Both presses happen at
     * the target before restoring — restoring between them would register as two separate clicks rather than
     * a double-click.
     */
    public static void doubleClick(Point p) {
        if (p == null) return;
        Debug.log("[Mouse] doubleClick " + p);
        NativeController controller = controller();
        java.awt.Point origin = controller.cursorPosition();
        move(p);
        pressAndRelease(controller, MouseButton.LEFT);
        pressAndRelease(controller, MouseButton.LEFT);
        PointerPolicy.restoreTo(controller, session(), origin);
    }

    /**
     * One press/release pair with the controller's own hold in between. Issued back to back they span less than a
     * frame at 60 fps, and a target sampling input once per frame can miss the press entirely — which is why
     * {@link NativeController#click} holds and why the double-click's two pairs are not bare
     * {@link #down(MouseButton)}/{@link #up(MouseButton)} calls.
     */
    private static void pressAndRelease(NativeController controller, MouseButton button) {
        controller.mouseButton(button.code(), true);
        pause(controller.pressHoldMs());
        controller.mouseButton(button.code(), false);
    }

    /** Sleep that keeps the interrupt flag — a gesture's pauses are not worth throwing over. */
    private static void pause(long ms) {
        if (ms <= 0) return;
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
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
        // Read the origin before the gesture; the cursor must stay with the drag until the button is
        // released, so this is restored once at the end rather than per-step.
        java.awt.Point origin = controller().cursorPosition();
        move(start);
        down(MouseButton.LEFT);
        if (durationMs > 0 && start != null && end != null) {
            int steps = Math.max(1, (int) (durationMs / 15));
            long perStep = durationMs / steps;
            for (int i = 1; i <= steps; i++) {
                double t = (double) i / steps;
                // Interpolate in double and round per step: the cursor lands on whole pixels either way,
                // and truncating instead would bias every step of the glide towards the start.
                move(new Point(
                        (int) Math.round(start.x() + (end.x() - start.x()) * t),
                        (int) Math.round(start.y() + (end.y() - start.y()) * t)));
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
        PointerPolicy.restoreTo(controller(), session(), origin);
        // After the release, not before the press: an observer drawing this is drawing something that
        // happened, and a gesture that threw part-way through is not one.
        if (Bots.hasObservers() && start != null && end != null) {
            Bots.fireSwipe(new SwipeEvent(Surface.ofScreen(), start, end, durationMs));
        }
    }

    /**
     * Scroll the wheel by {@code notches}. Positive scrolls up / away from you, negative scrolls
     * down / toward you. Prefer the clearer {@link #scrollUp(int)} / {@link #scrollDown(int)}.
     */
    @Internal("a signed notches is the ambiguity scrollUp/scrollDown remove; this method's own javadoc "
            + "already prefers them, and the menu offering all three as equals contradicted it")
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
