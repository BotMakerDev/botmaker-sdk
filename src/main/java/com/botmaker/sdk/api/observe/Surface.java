package com.botmaker.sdk.api.observe;

import com.botmaker.sdk.api.Rect;
import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.capture.Window;

/**
 * Identifies the surface a {@link BotObserver} event acted on — a specific {@link Window} (by title +
 * absolute bounds) or the whole screen. Lets an observer that renders overlays know which window/screen to
 * draw on. Purely descriptive: it holds no native handle.
 */
public record Surface(String title, Rect bounds) {

    /** True when this refers to a specific window (as opposed to the whole screen). */
    public boolean isWindow() {
        return title != null;
    }

    /** The whole virtual screen (the default {@code CaptureSource}). */
    public static Surface ofScreen() {
        return new Surface(null, null);
    }

    /** A specific window, by title and absolute bounds. */
    public static Surface ofWindow(String title, Rect bounds) {
        return new Surface(title, bounds);
    }

    /** Resolves the surface a capture originated from: a {@link Window}'s identity, else the screen. */
    public static Surface of(CaptureSource source) {
        if (source instanceof Window w) {
            return ofWindow(w.title(), w.bounds());
        }
        return ofScreen();
    }
}
