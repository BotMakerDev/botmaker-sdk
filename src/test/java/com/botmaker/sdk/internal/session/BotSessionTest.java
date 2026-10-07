package com.botmaker.sdk.internal.session;

import com.botmaker.session.Capability;
import com.botmaker.session.DesktopSession;
import com.botmaker.shared.capture.GenericWindow;
import com.botmaker.shared.capture.NativeController;
import com.botmaker.shared.launch.LaunchSpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The bot's session holder: set / get / isActive / clear, defaulting to none. Was the session module's. */
class BotSessionTest {

    @AfterEach
    void tearDown() {
        BotSession.clear();
    }

    @Test
    void defaultsToNoSession() {
        assertNull(BotSession.get());
        assertFalse(BotSession.isActive());
    }

    @Test
    void setThenGetReturnsTheSession() {
        StubSession session = new StubSession();
        BotSession.set(session);
        assertSame(session, BotSession.get());
        assertTrue(BotSession.isActive());
    }

    @Test
    void clearDetachesWithoutClosing() {
        StubSession session = new StubSession();
        BotSession.set(session);
        BotSession.clear();
        assertNull(BotSession.get());
        assertFalse(BotSession.isActive());
        assertFalse(session.closed, "clear() must not close the session — that is the setter's job");
    }

    /** A do-nothing session that only records whether it was closed. */
    private static final class StubSession implements DesktopSession {
        boolean closed;

        @Override public Set<Capability> capabilities() { return EnumSet.noneOf(Capability.class); }
        @Override public Rectangle screen() { return new Rectangle(); }
        @Override public String displayName() { return ":9"; }
        @Override public void attach(GenericWindow window) { }
        @Override public GenericWindow attached() { return null; }
        @Override public void launch(LaunchSpec spec) { }
        @Override public BufferedImage capture() { return null; }
        @Override public NativeController controller() { return null; }
        @Override public void close() { closed = true; }
    }
}
