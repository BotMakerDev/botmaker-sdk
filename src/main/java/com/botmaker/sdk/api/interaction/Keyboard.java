package com.botmaker.sdk.api.interaction;

import com.botmaker.sdk.api.util.Debug;
import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.capture.Source;
import com.botmaker.sdk.internal.capture.WindowBacked;
import com.botmaker.shared.capture.GenericWindow;
import com.botmaker.shared.capture.NativeController;
import com.botmaker.shared.capture.NativeControllerFactory;
import com.botmaker.session.ActiveSession;
import com.botmaker.session.DesktopSession;

/**
 * Simulated keyboard input. Keys are expressed with the OS-neutral {@link Key} enum; combos and
 * typing are provided for convenience. Backed per-OS by a pluggable input backend on Linux
 * (uinput/xdotool/XTest/XSendEvent — see {@code LinuxController}) and {@code keybd_event} on Windows, through
 * the internal native controller.
 *
 * <p>The no-argument methods target the project's ambient {@link Source#current() capture source} — the same
 * "where" every no-source vision/mouse call uses — so a bot configured to a game window types into that window
 * rather than whatever happens to have focus. Each also has a {@link CaptureSource}-first overload that
 * <em>targets</em> an explicit window — the keyboard counterpart of {@link Mouse#click(CaptureSource, int, int)}.
 * When the source has no single desktop window ({@code desktop()}/{@code monitor()}/an unopened window/an
 * emulator, i.e. {@link CaptureSource#targetWindow()} is {@code null}) the call transparently falls back to the
 * focused-window path.
 *
 * <p><b>What "targets a window" costs, on Linux.</b> Under the cursor-safe default backend the key is
 * delivered to the window in the background — but games and many toolkits reject those synthetic events, so
 * they see nothing. Once real input is enabled ({@code BotSettings.useRealInput(true)}, or anything else that
 * escalates the backend) the events come from a kernel virtual device that a game cannot tell from a real
 * keyboard — and such a device carries no window, so targeting is implemented as <em>raise the window, then
 * type</em>. Keys therefore reach the game, but the game is brought to the foreground first. There is no
 * mechanism on X11 that is both background and accepted by a game; that trade is the whole choice.
 */
public class Keyboard {

    /**
     * The controller keys are delivered through — the {@link ActiveSession}'s {@code :N}-bound controller when
     * an isolated session is registered, else the process-wide {@code :0} singleton (today's behaviour). See
     * {@link Mouse}'s equivalent choke point.
     */
    private static NativeController controller() {
        DesktopSession session = ActiveSession.get();
        return session != null ? session.controller() : NativeControllerFactory.get();
    }

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
        Debug.log("[Keyboard] tap " + key);
        press(key);
        release(key);
    }

    /**
     * Press a chord: hold every key in order, then release them in reverse order — e.g.
     * {@code Keyboard.combo(Key.CTRL, Key.C)} for copy.
     */
    public static void combo(Key... keys) {
        Debug.log("[Keyboard] combo " + java.util.Arrays.toString(keys));
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
        GenericWindow w = WindowBacked.of(source);
        Debug.log("[Keyboard] press " + key + (w == null ? " (focused window)" : " -> " + w.getTitle()));
        if (w == null) {
            controller().keyDown(key.nativeCode());
            return;
        }
        controller().keyDown(w, key.nativeCode());
    }

    /** Release a held {@code key} on {@code source}'s window. */
    public static void release(CaptureSource source, Key key) {
        GenericWindow w = WindowBacked.of(source);
        Debug.log("[Keyboard] release " + key + (w == null ? " (focused window)" : " -> " + w.getTitle()));
        if (w == null) {
            controller().keyUp(key.nativeCode());
            return;
        }
        controller().keyUp(w, key.nativeCode());
    }

    /** Press then release {@code key} on {@code source}'s window. */
    public static void tap(CaptureSource source, Key key) {
        Debug.log("[Keyboard] tap " + key + " on " + source);
        press(source, key);
        release(source, key);
    }

    /** Press a chord on {@code source}'s window: hold each key in order, release in reverse. */
    public static void combo(CaptureSource source, Key... keys) {
        Debug.log("[Keyboard] combo " + java.util.Arrays.toString(keys) + " on " + source);
        for (Key key : keys) {
            press(source, key);
        }
        for (int i = keys.length - 1; i >= 0; i--) {
            release(source, keys[i]);
        }
    }

    /** Type {@code text} into {@code source}'s window (see {@link #type(String)}). */
    public static void type(CaptureSource source, String text) {
        GenericWindow w = WindowBacked.of(source);
        Debug.log("[Keyboard] type \"" + text + "\""
                + (w == null ? " (focused window)" : " -> " + w.getTitle()));
        if (w == null) {
            controller().typeText(text);
            return;
        }
        controller().typeText(w, text);
    }
}
