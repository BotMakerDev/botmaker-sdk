package com.botmaker.sdk.internal.session;

import com.botmaker.shared.session.ActiveSession;
import com.botmaker.shared.session.NestedSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The bot-runtime producer's gate and backend/size selection — the pure part that decides <em>whether</em> and
 * <em>how</em> to go isolated. The live bring-up ({@code NestedSession.start} → launch → register) needs a real
 * X server and is verified by the shared live suite / manually, exactly like Studio's launcher.
 */
class SessionBootstrapTest {

    @AfterEach
    void tearDown() {
        System.clearProperty(SessionBootstrap.ISOLATED_PROPERTY);
        System.clearProperty(SessionBootstrap.BACKEND_PROPERTY);
        ActiveSession.clear();
    }

    @Test
    void isolationIsOffByDefault() {
        System.clearProperty(SessionBootstrap.ISOLATED_PROPERTY);
        assertFalse(SessionBootstrap.isolationRequested());
    }

    @Test
    void isolationRequestedWhenPropertyIsTrue() {
        System.setProperty(SessionBootstrap.ISOLATED_PROPERTY, "true");
        assertTrue(SessionBootstrap.isolationRequested());
    }

    @Test
    void launchIsolatedNoOpsWhenNotRequested() {
        System.clearProperty(SessionBootstrap.ISOLATED_PROPERTY);
        // Returns false (caller runs its normal :0 launch) and never registers a session.
        assertFalse(SessionBootstrap.launchIsolated(new com.botmaker.shared.launch.LaunchSpec(
                com.botmaker.shared.launch.LaunchKind.EXE, "/bin/true")));
        assertFalse(ActiveSession.isActive());
    }

    @Test
    void backendDefaultsToXephyrAndOptsIntoGamescope() {
        System.clearProperty(SessionBootstrap.BACKEND_PROPERTY);
        assertEquals(NestedSession.Backend.XEPHYR, SessionBootstrap.backend());
        System.setProperty(SessionBootstrap.BACKEND_PROPERTY, "gamescope");
        assertEquals(NestedSession.Backend.GAMESCOPE, SessionBootstrap.backend());
    }

    @Test
    void sizeFallsBackToTheDefaultWhenNoProjectResolution() {
        // No botmaker-project.properties on the test classpath → no authored resolution → the fallback size.
        int[] size = SessionBootstrap.size();
        assertEquals(SessionBootstrap.DEFAULT_WIDTH, size[0]);
        assertEquals(SessionBootstrap.DEFAULT_HEIGHT, size[1]);
    }

    @Test
    void optionsCarryTheSelectedBackendAndSize() {
        System.setProperty(SessionBootstrap.BACKEND_PROPERTY, "gamescope");
        NestedSession.Options o = SessionBootstrap.options();
        assertEquals(NestedSession.Backend.GAMESCOPE, o.backend());
        assertEquals(SessionBootstrap.DEFAULT_WIDTH, o.width());
        assertEquals(SessionBootstrap.DEFAULT_HEIGHT, o.height());
    }
}
