package com.botmaker.sdk.internal.capture;

import com.botmaker.shared.capture.FrameGrab;
import com.botmaker.shared.capture.GenericWindow;

import java.util.function.Function;

/**
 * The native window behind an {@code api} {@link Window}, for {@link WindowBacked#of} — without a public
 * method on {@code Window} that names botmaker-shared's {@code GenericWindow}.
 *
 * <p>Not a method on {@code Window}: an interface method is public, and so would put a type a bot cannot name
 * on the surface a bot compiles against. {@code Window} grants its
 * accessor here once, from its own static initialiser, which has run by the time any instance exists to ask
 * about.
 */
public final class WindowHandles {

    private static volatile Function<Window, GenericWindow> access;

    private WindowHandles() {}

    /** Called once, by {@code Window}; a second grant is refused so nothing else can replace the accessor. */
    public static void grant(Function<Window, GenericWindow> accessor) {
        synchronized (WindowHandles.class) {
            if (access != null) throw new IllegalStateException("Window's accessor is already granted");
            access = accessor;
        }
    }

    /** The window's native handle, or {@code null} for none. */
    static GenericWindow of(Window window) {
        Function<Window, GenericWindow> accessor = access;
        return window == null || accessor == null ? null : accessor.apply(window);
    }

    /**
     * Frames of {@code window} read again and again for a live feed, what each read needs kept open until the grab is
     * closed (2026-10-22): here, not on {@code Window}, for the same reason as its handle.
     */
    public static FrameGrab grab(Window window) {
        return Window.controller().grabWindow(of(window));
    }

    /** What names {@code window} among the open ones: equal for two {@code Window}s of one native window. */
    public static Object id(Window window) {
        GenericWindow handle = of(window);
        return handle == null ? null : handle.getNativeHandle();
    }
}
