package com.botmaker.sdk.internal.session;

import com.botmaker.session.DesktopSession;

/**
 * The {@link DesktopSession} this bot is driving, if any. When set, {@code Mouse}/{@code Keyboard} route their
 * controller through it and the ambient capture {@code Source} follows its window, so a bot drives its private
 * {@code :N} display exactly as it would drive {@code :0} — with no call-site change.
 *
 * <p>Here, in the bot runtime, rather than in {@code botmaker-session}: "the one session this process drives" is a
 * fact about a bot run — a graph of static facades with no object to thread a session through — and not about
 * sessions, so the library keeps no record of which one a bot uses (its only process-wide state is the registry
 * its orphan sweep spares). {@code null} means "no session" — the default, which keeps a bot on
 * its desktop.
 *
 * <p>This holder does <b>not</b> own the session's lifecycle: whoever {@link #set} it ({@link SessionBootstrap},
 * or a test) closes the session and {@link #clear}s the holder.
 */
public final class BotSession {

    private static volatile DesktopSession current;

    private BotSession() {}

    /** Register {@code session} as the one this bot drives, or {@code null} to detach. */
    public static void set(DesktopSession session) {
        current = session;
    }

    /** The session this bot drives, or {@code null} when it runs on its desktop. */
    public static DesktopSession get() {
        return current;
    }

    /** Whether a session is registered. */
    public static boolean isActive() {
        return current != null;
    }

    /** Detach any session. Does <b>not</b> close it — that is the setter's responsibility. */
    public static void clear() {
        current = null;
    }
}
