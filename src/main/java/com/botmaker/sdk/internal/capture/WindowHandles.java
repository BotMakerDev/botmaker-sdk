package com.botmaker.sdk.internal.capture;

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
}
