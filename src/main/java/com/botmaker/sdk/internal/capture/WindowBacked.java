package com.botmaker.sdk.internal.capture;

import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.shared.capture.GenericWindow;

/**
 * A {@link CaptureSource} that is backed by a real on-screen application window — the seam that lets
 * {@code Keyboard} deliver keys to <em>that</em> window specifically (the keyboard counterpart of
 * {@link CaptureSource#click}).
 *
 * <h2>Why this is not a method on {@code CaptureSource}</h2>
 *
 * <p>It was one, until 1.1.0: {@code CaptureSource.targetWindow()} and {@code Window.targetWindow()}, both
 * returning {@link GenericWindow}. That put a {@code botmaker-shared} type into the SDK surface that is under
 * contract from 1.1.0, and shared is explicitly <em>freely breakable</em> — so a rename over in shared would
 * have broken a bot's contract with no gate on either side able to see it. Nothing but keyboard routing ever
 * called it, and no bot could do anything with the handle, so the honest fix was to move the capability out
 * of {@code api} rather than to promise shared's spelling forever.
 *
 * <p>Implemented by the sources that resolve to a desktop window — {@link com.botmaker.sdk.api.capture.Window},
 * {@link NamedWindow}, {@link SessionSource} and a {@link CaptureSource#region region} of one. Everything else
 * (the desktop, a monitor, an emulator, a window that isn't open yet) simply does not implement it, which is
 * how {@link #of} answers {@code null} and keyboard input falls back to the global focused-window path.
 */
public interface WindowBacked {

    /**
     * The native OS window this source represents, or {@code null} when it is not currently resolvable (a
     * {@code Window} whose title matches nothing right now still implements this interface — it is the kind
     * of thing that has a window, it just has not found one yet).
     */
    GenericWindow targetWindow();

    /**
     * The window behind {@code source}, or {@code null} when it has none — the one call site shape every
     * consumer wants, so nobody re-writes the {@code instanceof} and gets the null handling subtly different.
     */
    static GenericWindow of(CaptureSource source) {
        return source instanceof WindowBacked backed ? backed.targetWindow() : null;
    }
}
