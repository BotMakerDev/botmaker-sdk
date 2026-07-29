package com.botmaker.sdk.internal.session;

import com.botmaker.shared.launch.LaunchKind;
import com.botmaker.shared.launch.LaunchSpec;
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
        assertFalse(SessionBootstrap.launchIsolated(new LaunchSpec(LaunchKind.EXE, "/bin/true")));
        assertFalse(ActiveSession.isActive());
    }

    @Test
    void backendAutoSelectsFromKindAndHonoursOverride() {
        System.clearProperty(SessionBootstrap.BACKEND_PROPERTY);
        // Kind-driven with no override: a plain command → Xephyr, a game → gamescope.
        assertEquals(NestedSession.Backend.XEPHYR,
                SessionBootstrap.backend(new LaunchSpec(LaunchKind.CLI, "echo hi")));
        assertEquals(NestedSession.Backend.GAMESCOPE,
                SessionBootstrap.backend(new LaunchSpec(LaunchKind.HEROIC, "Firestone")));
        // The explicit override wins over the kind-driven default (forces Xephyr even for a game).
        System.setProperty(SessionBootstrap.BACKEND_PROPERTY, "xephyr");
        assertEquals(NestedSession.Backend.XEPHYR,
                SessionBootstrap.backend(new LaunchSpec(LaunchKind.HEROIC, "Firestone")));
        System.setProperty(SessionBootstrap.BACKEND_PROPERTY, "gamescope");
        assertEquals(NestedSession.Backend.GAMESCOPE,
                SessionBootstrap.backend(new LaunchSpec(LaunchKind.CLI, "echo hi")));
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
        NestedSession.Options o = SessionBootstrap.options(new LaunchSpec(LaunchKind.CLI, "echo hi"));
        assertEquals(NestedSession.Backend.GAMESCOPE, o.backend());
        assertEquals(SessionBootstrap.DEFAULT_WIDTH, o.width());
        assertEquals(SessionBootstrap.DEFAULT_HEIGHT, o.height());
    }

    @Test
    void optionsTrackTheLaunchKindWithoutAnOverride() {
        System.clearProperty(SessionBootstrap.BACKEND_PROPERTY);
        assertEquals(NestedSession.Backend.GAMESCOPE,
                SessionBootstrap.options(new LaunchSpec(LaunchKind.STEAM, "570")).backend());
        assertEquals(NestedSession.Backend.XEPHYR,
                SessionBootstrap.options(new LaunchSpec(LaunchKind.CLI, "echo hi")).backend());
    }
}
