package com.botmaker.sdk.api.capture;

import com.botmaker.sdk.api.Point;

import java.awt.image.BufferedImage;
import java.util.Optional;

/**
 * A {@link CaptureSource} that targets a window <em>by title substring</em>, resolved lazily on every
 * use via {@link Window#find(String)}. Unlike a concrete {@link Window} (which binds a native handle at
 * construction), this survives the window not existing yet and re-binds if the window reopens or moves —
 * which is what lets a bot point at a game before it has launched and lets
 * {@link com.botmaker.sdk.api.launch.Game} tell whether that game is currently running.
 *
 * <p>Obtain one via {@link CaptureSource#window(String)}.
 */
public final class NamedWindow implements CaptureSource {

    private final String titleSubstring;

    NamedWindow(String titleSubstring) {
        this.titleSubstring = titleSubstring;
    }

    private Optional<Window> resolve() {
        return Window.find(titleSubstring);
    }

    @Override
    public BufferedImage capture() {
        return resolve().map(Window::capture).orElse(null);
    }

    @Override
    public Point origin() {
        return resolve().map(Window::origin).orElseGet(() -> CaptureSource.desktop().origin());
    }

    @Override
    public boolean isPresent() {
        return resolve().isPresent();
    }

    /** A title-matched window is the one source whose {@link #isPresent()} is a real "is it up?" answer. */
    @Override
    public boolean hasWindowIdentity() {
        return true;
    }

    /** The live window when present (so observers see its identity), else this. */
    @Override
    public CaptureSource base() {
        return resolve().map(w -> (CaptureSource) w).orElse(this);
    }

    /** The resolved window's native handle when open, else {@code null} (→ global focused-window keys). */
    @Override
    public com.botmaker.shared.capture.GenericWindow targetWindow() {
        return resolve().map(Window::targetWindow).orElse(null);
    }

    @Override
    public String toString() {
        return "Window[title~=" + titleSubstring + "]";
    }
}
