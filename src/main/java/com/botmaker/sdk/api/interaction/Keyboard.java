package com.botmaker.sdk.api.interaction;

import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.capture.Source;
import com.botmaker.shared.capture.GenericWindow;
import com.botmaker.shared.capture.NativeControllerFactory;

/**
 * Simulated keyboard input. Keys are expressed with the OS-neutral {@link Key} enum; combos and
 * typing are provided for convenience. Backed per-OS by XTest (Linux) / {@code keybd_event}
 * (Windows) through the internal native controller.
 *
 * <p>The no-argument methods target the project's ambient {@link Source#current() capture source} — the same
 * "where" every no-source vision/mouse call uses — so a bot configured to a game window types into that window
 * rather than whatever happens to have focus. Each also has a {@link CaptureSource}-first overload that
 * <em>targets</em> an explicit window — the keyboard counterpart of {@link Mouse#click(CaptureSource, int, int)}.
 * When the source has no single desktop window ({@code desktop()}/{@code monitor()}/an unopened window/an
 * emulator, i.e. {@link CaptureSource#targetWindow()} is {@code null}) the call transparently falls back to the
 * focused-window path.
 */
public class Keyboard {

    /** Press and hold a key on the ambient {@link Source} (remember to {@link #release}). */
    public static void press(Key key) {
        press(Source.current(), key);
    }

    /** Release a held key on the ambient {@link Source}. */
    public static void release(Key key) {
        release(Source.current(), key);
    }

    /** Press then release a key. */
    public static void tap(Key key) {
        press(key);
        release(key);
    }

    /**
     * Press a chord: hold every key in order, then release them in reverse order — e.g.
     * {@code Keyboard.combo(Key.CTRL, Key.C)} for copy.
     */
    public static void combo(Key... keys) {
        for (Key key : keys) {
            press(key);
        }
        for (int i = keys.length - 1; i >= 0; i--) {
            release(keys[i]);
        }
    }

    /**
     * Type a string on the ambient {@link Source}, handling shifting for uppercase / shifted characters. Best
     * effort for non-ASCII input (falls back to the platform's keysym/VK mapping).
     */
    public static void type(String text) {
        type(Source.current(), text);
    }

    // --- Targeted overloads: deliver to a specific window instead of whatever has focus ---

    /** Press and hold {@code key}, delivered to {@code source}'s window (remember to {@link #release}). */
    public static void press(CaptureSource source, Key key) {
        GenericWindow w = source == null ? null : source.targetWindow();
        if (w == null) {
            NativeControllerFactory.get().keyDown(key.nativeCode());
            return;
        }
        NativeControllerFactory.get().keyDown(w, key.nativeCode());
    }

    /** Release a held {@code key} on {@code source}'s window. */
    public static void release(CaptureSource source, Key key) {
        GenericWindow w = source == null ? null : source.targetWindow();
        if (w == null) {
            NativeControllerFactory.get().keyUp(key.nativeCode());
            return;
        }
        NativeControllerFactory.get().keyUp(w, key.nativeCode());
    }

    /** Press then release {@code key} on {@code source}'s window. */
    public static void tap(CaptureSource source, Key key) {
        press(source, key);
        release(source, key);
    }

    /** Press a chord on {@code source}'s window: hold each key in order, release in reverse. */
    public static void combo(CaptureSource source, Key... keys) {
        for (Key key : keys) {
            press(source, key);
        }
        for (int i = keys.length - 1; i >= 0; i--) {
            release(source, keys[i]);
        }
    }

    /** Type {@code text} into {@code source}'s window (see {@link #type(String)}). */
    public static void type(CaptureSource source, String text) {
        GenericWindow w = source == null ? null : source.targetWindow();
        if (w == null) {
            NativeControllerFactory.get().typeText(text);
            return;
        }
        NativeControllerFactory.get().typeText(w, text);
    }
}
