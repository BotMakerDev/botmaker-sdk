package com.botmaker.sdk.internal.capture;

import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.console.Debug;
import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.shared.capture.GenericWindow;
import com.botmaker.shared.launch.GameWindows;
import com.botmaker.shared.launch.WindowIdentity;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Optional;

/**
 * A {@link CaptureSource} that targets one window <em>by its identity</em> — its window class, and its exact title
 * when that class is shared by unrelated programs ({@link WindowIdentity}) — resolved lazily on every use via
 * {@link Window#named}. Unlike a concrete {@link Window} (which binds a native handle at construction), this
 * survives the window not existing yet and re-binds if the window reopens or moves — which is what lets a bot
 * point at a game before it has launched and lets {@link com.botmaker.sdk.internal.launch.Game} tell whether that
 * game is currently running.
 *
 * <p>Never a window by its title alone (2026-10-13): Studio's own "BotMaker Blocks - Firestone" was taken for the
 * game and clicked in. Obtain one via {@link CaptureSource#window(String, String)}.
 */
public final class NamedWindow implements CaptureSource, WindowBacked {

    private final String windowClass;
    private final String title;

    public NamedWindow(String windowClass, String title) {
        this.windowClass = windowClass == null ? "" : windowClass.trim();
        this.title = title == null ? "" : title.trim();
    }

    /**
     * The window class this matches — the text the user picked, never resolved.
     *
     * <p>Here for the same reason {@link Monitor#index()} is: the host writes a capture source through its
     * components and a picker labels one on screen, and both need the arguments back.
     */
    public String windowClass() {
        return windowClass;
    }

    /** The exact title this also matches, or {@code ""} when the class alone names the window. */
    public String title() {
        return title;
    }

    /** The source that finds the window {@code identity} names. */
    public static NamedWindow of(WindowIdentity identity) {
        return new NamedWindow(identity.windowClass(), identity.title());
    }

    /**
     * What this names, or empty when it names no one window — no class, or a generic class with no title: then
     * it matches nothing.
     */
    public Optional<WindowIdentity> identity() {
        return Optional.ofNullable(WindowIdentity.of(windowClass, title));
    }

    /** The window among {@code windows} this names, BotMaker's own never ({@link GameWindows#named}). */
    public Optional<GenericWindow> find(List<GenericWindow> windows) {
        return identity().flatMap(game -> GameWindows.named(game, windows));
    }

    /** How a person reads it: the title when there is one, else the class. */
    public String label() {
        return title.isEmpty() ? windowClass : title;
    }

    private Optional<Window> resolve() {
        return identity().flatMap(Window::named);
    }

    @Override
    public BufferedImage capture() {
        Optional<Window> w = resolve();
        if (w.isEmpty()) {
            Debug.error("no window " + this + " is open — capture returns null");
            return null;
        }
        return w.get().capture();
    }

    @Override
    public Point origin() {
        return resolve().map(Window::origin).orElseGet(() -> CaptureSource.desktop().origin());
    }

    @Override
    public boolean isPresent() {
        return resolve().isPresent();
    }

    /** A window known by its class is the one source whose {@link #isPresent()} is a real "is it up?" answer. */
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
    public GenericWindow targetWindow() {
        return resolve().map(WindowHandles::of).orElse(null);
    }

    @Override
    public String toString() {
        return "Window[" + identity().map(WindowIdentity::spec).orElse("no class") + "]";
    }
}
