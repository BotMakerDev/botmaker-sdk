package com.botmaker.sdk.api.vision;

import com.botmaker.plugin.api.palette.Palette;
import com.botmaker.sdk.api.bot.BotSettings;
import com.botmaker.sdk.api.bot.PopupGuard;
import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.capture.Source;
import com.botmaker.sdk.api.console.Debug;
import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.time.Wait;

import java.awt.image.BufferedImage;
import java.util.List;

/**
 * Scrolls and drags anchored on a picture, each proved by that picture moving: found again at another place, or
 * gone from the screen. A list at its end does not move, so the answer is false there, and a page loop reads it:
 *
 * <pre>{@code
 * ImageFinder.untilFind(Pictures.LIST_END, () -> ImageScroller.scrollAt(Pictures.LIST_ITEM, -3));
 * }</pre>
 *
 * <p>A gesture on a place cannot tell whether it landed, as {@code Mouse.click} on a spot cannot; these find the
 * picture first, act on it, and give the game the bot's patience ({@link BotSettings#patience()}) to move it.
 * Each also has the {@link CaptureSource} form {@link ImageClicker} has; an emulator source swipes.
 */
@Palette(category = "vision", categoryLabel = "Vision", icon = "👆")
public final class ImageScroller {

    /** How far, in pixels, a picture's centre has to go to have moved, past a match's own jitter. */
    static final int MOVED_PX = 4;

    /** How long a drag glides: a game that reads a drag's speed sees a gesture, not a jump. */
    static final long DRAG_MS = 300;

    private ImageScroller() {}

    /**
     * Turns the wheel {@code notches} over {@code picture}: positive up or away from you, negative down.
     *
     * @return true when the picture moved; false when it is not on the screen or did not move
     */
    public static boolean scrollAt(ImageTemplate picture, int notches) {
        return scrollAt(picture, notches, Source.current());
    }

    /** {@link #scrollAt(ImageTemplate, int)} on {@code source}. */
    public static boolean scrollAt(ImageTemplate picture, int notches, CaptureSource source) {
        PopupGuard.check();
        MatchResult at = ImageFinder.findInternal(picture, source, ImageFinder.confidence());
        Vision.setLastMatch(at);
        if (!at.isFound()) return false;
        source.scroll(at.center(), notches);
        return movedAway(picture, at, source, "Scrolled " + notches + " at");
    }

    /**
     * Presses on {@code picture} and drags it by {@code dx}, {@code dy}, each a fraction of the capture source's
     * width and height (the game's window, for a window source), stopping at its edge:
     * {@code drag(LIST_ITEM, 0, -0.4)} pulls a list up by two fifths of the window.
     *
     * @return true when the picture moved; false when it is not on the screen or did not move
     */
    public static boolean drag(ImageTemplate picture, double dx, double dy) {
        return drag(picture, dx, dy, Source.current());
    }

    /** {@link #drag(ImageTemplate, double, double)} on {@code source}. */
    public static boolean drag(ImageTemplate picture, double dx, double dy, CaptureSource source) {
        PopupGuard.check();
        BufferedImage frame = source.capture();
        MatchResult at = frame == null ? MatchResult.notFound() : best(frame, picture, source);
        Vision.setLastMatch(at);
        if (!at.isFound()) return false;
        Point from = at.center();
        Point to = inside(new Point(from.x() + (int) Math.round(dx * frame.getWidth()),
                from.y() + (int) Math.round(dy * frame.getHeight())), frame, source.origin());
        source.drag(from, to, DRAG_MS);
        return movedAway(picture, at, source, "Dragged to " + to + " from");
    }

    /**
     * Drags {@code from} onto {@code to}, both found in one frame: a hero onto a slot.
     *
     * @return true when {@code from} moved; false when either is not on the screen or {@code from} did not move
     */
    public static boolean drag(ImageTemplate from, ImageTemplate to) {
        return drag(from, to, Source.current());
    }

    /** {@link #drag(ImageTemplate, ImageTemplate)} on {@code source}. */
    public static boolean drag(ImageTemplate from, ImageTemplate to, CaptureSource source) {
        PopupGuard.check();
        BufferedImage frame = source.capture();
        if (frame == null) return false;
        MatchResult start = best(frame, from, source);
        MatchResult end = best(frame, to, source);
        Vision.setLastMatch(start);
        if (!start.isFound() || !end.isFound()) return false;
        source.drag(start.center(), end.center(), DRAG_MS);
        return movedAway(from, start, source, "Dragged onto " + end.templateId() + " from");
    }

    /** The best match of {@code picture} on {@code frame}, or not found. */
    private static MatchResult best(BufferedImage frame, ImageTemplate picture, CaptureSource source) {
        List<MatchResult> all = ImageFinder.findAllIn(frame, picture, source, ImageFinder.confidence());
        MatchResult best = MatchResult.notFound();
        for (MatchResult each : all) {
            if (each.isFound() && (!best.isFound() || each.confidence() > best.confidence())) best = each;
        }
        return best;
    }

    /**
     * Whether {@code picture}, last seen at {@code before}, leaves that place within the bot's patience: no copy of
     * it is there any more. A twin further down a list taking the best match does not count as still, and a
     * picture gone from the screen (scrolled off, or covered) counts as moved. Looked at once at least, even with
     * no patience.
     */
    private static boolean movedAway(ImageTemplate picture, MatchResult before, CaptureSource source, String did) {
        Point was = before.center();
        long deadline = System.currentTimeMillis() + BotSettings.current().patience();
        boolean moved;
        do {
            Wait.milliseconds(Rounds.POLL_MS);
            moved = !stillAt(was, picture, source);
        } while (!moved && System.currentTimeMillis() < deadline && !Thread.currentThread().isInterrupted());
        if (Debug.isEnabled()) {
            Debug.log(did + " " + before.templateId() + " " + was + (moved ? ": it moved." : ": it did not move."));
        }
        return moved;
    }

    /** Whether some copy of {@code picture} is on {@code source} within {@link #MOVED_PX} of {@code was}. */
    private static boolean stillAt(Point was, ImageTemplate picture, CaptureSource source) {
        BufferedImage frame = source.capture();
        if (frame == null) return false;
        for (MatchResult each : ImageFinder.findAllIn(frame, picture, source, ImageFinder.confidence())) {
            if (each.isFound() && !moved(was, each.center())) return true;
        }
        return false;
    }

    static boolean moved(Point was, Point now) {
        return Math.abs(now.x() - was.x()) + Math.abs(now.y() - was.y()) > MOVED_PX;
    }

    /** {@code p} kept inside {@code frame} as it lies on the screen at {@code origin}. */
    private static Point inside(Point p, BufferedImage frame, Point origin) {
        return new Point(Math.clamp(p.x(), origin.x(), origin.x() + frame.getWidth() - 1),
                Math.clamp(p.y(), origin.y(), origin.y() + frame.getHeight() - 1));
    }
}
