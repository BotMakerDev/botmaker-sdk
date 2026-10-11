package com.botmaker.sdk.internal.vision;

import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.geometry.Size;
import com.botmaker.sdk.internal.capture.WindowBacked;
import com.botmaker.shared.Diag;
import com.botmaker.shared.capture.GenericWindow;
import com.botmaker.shared.capture.NativeControllerFactory;

import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.Optional;

/**
 * The bot's screen size at run time: what {@code @SdkValue(SdkValue.Id.SCREEN)} in {@code Sdk.java} says, handed
 * here by the run before the first click (2026-10-11).
 *
 * <p>It is the size of the frame the bot's pictures were cut from. A picture that names no frame of its own
 * ({@code .in(Size)}) was cut from this one, so the matcher rescales it by the live frame against this size; a run
 * that starts on a window of another size resizes it ({@link #fit}). A bot that names no size has none: its
 * pictures are matched as they are, as they always were.
 */
public final class BotScreen {

    private static final String SOURCE = "Screen";
    /** How long a resized window is given to redraw at its new size before it is measured again. */
    private static final long SETTLE_MS = 500;

    private static volatile Size size;

    private BotScreen() {}

    /** Takes the bot's size, as the run hands it over; an empty one ({@code 0×0}) is no size. */
    public static void use(Size screen) {
        size = screen == null || screen.empty() ? null : screen;
    }

    /** The bot's screen size, when it names one. */
    public static Optional<Size> size() {
        return Optional.ofNullable(size);
    }

    /** {@link #size()} in the form shared's matcher takes, or null. */
    public static Dimension dimension() {
        Size screen = size;
        return screen == null ? null : new Dimension(screen.width(), screen.height());
    }

    /**
     * Brings {@code source} to the bot's size at a run's start, or says in the trace why it is not: a window is
     * resized, keeping its border; anything else is reported, since the screen is the game's setting or the
     * user's monitor. Its pictures are scaled either way, which costs exactness, not the run.
     *
     * @return the size the source now shows, or empty when it could not be captured
     */
    public static Optional<Size> fit(CaptureSource source) {
        Size wanted = size;
        if (wanted == null || source == null) return Optional.empty();
        // The whole surface: a region of it is never the size its pictures' frame was.
        CaptureSource whole = source.base();
        Optional<Size> live = measure(whole);
        if (live.isEmpty() || live.get().equals(wanted)) return live;
        GenericWindow window = WindowBacked.of(whole);
        if (window != null && window.getRect() != null) {
            Rectangle outer = window.getRect();
            try {
                // The window's bounds are logical pixels, the frame physical: a HiDPI desktop shows a window
                // of w logical pixels as a frame of w × scale, its border included.
                int scale = Math.max(1, Math.round((float) live.get().width() / Math.max(1, outer.width)));
                NativeControllerFactory.get().resizeWindow(window,
                        wanted.width() / scale + Math.max(0, outer.width - live.get().width() / scale),
                        wanted.height() / scale + Math.max(0, outer.height - live.get().height() / scale));
                Thread.sleep(SETTLE_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (RuntimeException | LinkageError refused) {
                Diag.warn(SOURCE, "Could not resize the game window: " + refused.getMessage());
            }
            live = measure(whole);
            if (live.isEmpty() || live.get().equals(wanted)) return live;
        }
        Diag.warn(SOURCE, "The game shows " + live.get() + " but the bot's pictures were cut at " + wanted
                + " (Sdk.screen()): they are scaled, which can miss. Set the game to " + wanted
                + (window == null ? "." : ", or let its window be resized.")
                + (Places.otherShape(wanted, live.get()) ? " Its shape differs too: pictures scale by the smaller"
                + " side, and spots and zones stay with their anchor." : ""));
        return live;
    }

    /** The size {@code source} shows now, from one grab; empty when it could not be captured. */
    static Optional<Size> measure(CaptureSource source) {
        try {
            BufferedImage frame = source.capture();
            return frame == null ? Optional.empty() : Optional.of(new Size(frame.getWidth(), frame.getHeight()));
        } catch (RuntimeException | LinkageError unavailable) {
            return Optional.empty();
        }
    }
}
