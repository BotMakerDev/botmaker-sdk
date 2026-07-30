package com.botmaker.sdk.api.interaction;

import com.botmaker.sdk.api.Point;
import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.capture.SessionSource;
import com.botmaker.sdk.api.capture.Source;
import com.botmaker.sdk.internal.capture.core.RecordingNativeController;
import com.botmaker.shared.capture.NativeControllerFactory;
import com.botmaker.session.ActiveSession;
import com.botmaker.session.impl.HostSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase B routing: when an {@link ActiveSession} is registered the SDK's input facades drive the session's
 * controller and the ambient {@link Source} follows the session's owned window — and with none registered the
 * behaviour is byte-for-byte today's (the global {@code :0} controller / project-default source).
 */
class SessionRoutingTest {

    private RecordingNativeController globalFake;
    private RecordingNativeController sessionFake;
    private HostSession session;

    @BeforeEach
    void setUp() {
        globalFake = new RecordingNativeController();
        NativeControllerFactory.setForTesting(globalFake);
        sessionFake = new RecordingNativeController();
        // A HostSession over a distinct controller stands in for a NestedSession's :N-bound controller — same
        // DesktopSession seam, no live X server needed. Its attached window is the recording controller's.
        session = new HostSession(sessionFake);
        session.attach(sessionFake.window);
    }

    @AfterEach
    void tearDown() {
        ActiveSession.clear();
        Source.set(null);
        NativeControllerFactory.setForTesting(null);
    }

    @Test
    void inputRoutesToTheSessionControllerWhenActive() {
        ActiveSession.set(session);
        Mouse.click(new Point(10, 20));
        Keyboard.type("hi");
        assertFalse(sessionFake.events.isEmpty(), "input should have gone to the session controller");
        assertTrue(globalFake.events.isEmpty(), "nothing should have gone to the global :0 controller");
    }

    @Test
    void inputRoutesToTheGlobalControllerWhenNoSession() {
        Mouse.click(new Point(10, 20));
        assertFalse(globalFake.events.isEmpty(), "input should use the global controller when no session is set");
        assertTrue(sessionFake.events.isEmpty(), "the session controller must not be touched when inactive");
    }

    @Test
    void ambientSourceFollowsTheSessionWindow() {
        ActiveSession.set(session);
        CaptureSource current = Source.current();
        assertInstanceOf(SessionSource.class, current);
        // origin is the attached window's on-screen top-left (RecordingNativeController's 100,50 rect)
        assertEquals(100, current.origin().x);
        assertEquals(50, current.origin().y);
        assertEquals(sessionFake.window, current.targetWindow());
        assertTrue(current.isPresent());
    }

    @Test
    void anExplicitPinWinsOverTheActiveSession() {
        ActiveSession.set(session);
        CaptureSource pinned = CaptureSource.desktop();
        Source.set(pinned);
        assertSame(pinned, Source.current(), "an explicit Source.set must win even while a session is active");
        // Clearing the pin hands control back to the session.
        Source.set(null);
        assertInstanceOf(SessionSource.class, Source.current());
    }

    @Test
    void withNoSessionTheSourceIsTheProjectDefault() {
        CaptureSource current = Source.current();
        assertFalse(current instanceof SessionSource, "no session → no SessionSource, today's default path");
    }
}
