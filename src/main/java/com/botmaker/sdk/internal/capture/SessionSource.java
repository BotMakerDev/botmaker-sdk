package com.botmaker.sdk.internal.capture;

import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.capture.Source;
import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.shared.capture.GenericWindow;
import com.botmaker.session.DesktopSession;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;

/**
 * The ambient {@link CaptureSource} while a bot drives a private {@link DesktopSession} (a nested {@code :N}
 * display). It is where the "one window, no capture target" answer lands in the SDK: a nested session
 * <em>owns exactly one window</em> — the game it launched — so there is nothing to select by title.
 * {@link #capture()} is the session's own frame of that window, {@link #targetWindow()} is that window (so
 * keyboard input addresses it), and {@link #origin()} is its on-screen top-left so match coordinates convert
 * to clickable absolute points exactly as for any window source.
 *
 * <p>Chosen by {@link Source#current()} whenever a session is registered, ahead of the project's configured
 * {@code capture.source} — which still governs non-isolated bots. Stateless over the session: it re-reads
 * {@link DesktopSession#attached()} on every call, so it survives the game not having mapped its window yet
 * ({@code capture()} returns {@code null}, {@link #isPresent()} is {@code false}) and follows the window if it
 * moves.
 */
public final class SessionSource implements CaptureSource {

    private final DesktopSession session;

    public SessionSource(DesktopSession session) {
        this.session = session;
    }

    @Override
    public BufferedImage capture() {
        return session.capture();
    }

    @Override
    public Point origin() {
        GenericWindow w = session.attached();
        if (w == null) {
            return new Point(0, 0);
        }
        Rectangle r = w.getRect();
        return r == null ? new Point(0, 0) : new Point(r.x, r.y);
    }

    @Override
    public boolean isPresent() {
        return session.attached() != null;
    }

    @Override
    public boolean hasWindowIdentity() {
        // A nested session is defined by the single window it launched — that is a real presence to probe.
        return true;
    }

    @Override
    public GenericWindow targetWindow() {
        return session.attached();
    }

    @Override
    public String toString() {
        GenericWindow w = session.attached();
        return "SessionSource[" + (w == null ? "no window yet" : w.getTitle()) + "]";
    }
}
