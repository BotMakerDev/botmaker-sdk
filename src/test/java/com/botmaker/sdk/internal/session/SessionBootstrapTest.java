package com.botmaker.sdk.internal.session;

import com.botmaker.sdk.api.bot.Session;
import com.botmaker.shared.launch.LaunchKind;
import com.botmaker.shared.launch.LaunchSpec;
import com.botmaker.session.display.SessionBackends;
import com.botmaker.session.ActiveSession;
import com.botmaker.session.impl.NestedSession;
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
        // Session's overrides are static and outrank everything below them — a leak would silently pin every
        // later test in this JVM.
        Session.clearOverrides();
        ActiveSession.clear();
    }

    @Test
    void anExplicitSessionCallOutranksTheSystemProperty() {
        // The top rung of the ladder: bot code must be able to force its own behaviour on a machine whose
        // environment says the opposite, in both directions.
        System.setProperty(SessionBootstrap.ISOLATED_PROPERTY, "false");
        Session.enable();
        assertTrue(SessionBootstrap.isolationRequested());

        System.setProperty(SessionBootstrap.ISOLATED_PROPERTY, "true");
        Session.disable();
        assertFalse(SessionBootstrap.isolationRequested());
    }

    @Test
    void isEnabledReportsTheResolvedAnswerNotJustWhatBotCodeAsked() {
        // Session.isEnabled() is the whole ladder, so a bot that never calls anything still reads the truth.
        System.setProperty(SessionBootstrap.ISOLATED_PROPERTY, "false");
        assertFalse(Session.isEnabled());
        Session.enable();
        assertTrue(Session.isEnabled());
    }

    @Test
    void useBackendOutranksThePropertyAndAutoUnpinsToTheRungBelow() {
        System.setProperty(SessionBootstrap.BACKEND_PROPERTY, "xephyr");
        Session.useBackend("gamescope");
        assertEquals(NestedSession.Backend.GAMESCOPE,
                SessionBootstrap.backend(new LaunchSpec(LaunchKind.CLI, "echo hi")));

        // "auto" is not a backend: it un-pins, dropping to the next rung (here, the xephyr property).
        Session.useBackend("auto");
        assertEquals(NestedSession.Backend.XEPHYR,
                SessionBootstrap.backend(new LaunchSpec(LaunchKind.HEROIC, "Firestone")));
    }

    @Test
    void autoAndTyposDoNotSilentlyPinXephyr() {
        // The regression this ladder's total parse fixes: `session.backend=auto` (and any typo) used to hit
        // `"gamescope".equalsIgnoreCase(x) ? GAMESCOPE : XEPHYR` and pin a game to Xephyr's software GL — the
        // exact crash the kind-driven choice exists to prevent. Both must fall through to the kind.
        for (String value : new String[]{"auto", "gamescpoe", ""}) {
            System.setProperty(SessionBootstrap.BACKEND_PROPERTY, value);
            assertEquals(NestedSession.Backend.GAMESCOPE,
                    SessionBootstrap.backend(new LaunchSpec(LaunchKind.HEROIC, "Firestone")),
                    "a game must still get gamescope with session.backend='" + value + "'");
        }
    }

    @Test
    void isolationIsOnByDefault() {
        // No project file on the test classpath → session.isolated defaults to true → isolation on by default.
        System.clearProperty(SessionBootstrap.ISOLATED_PROPERTY);
        assertTrue(SessionBootstrap.isolationRequested());
    }

    @Test
    void systemPropertyOverridesToOff() {
        // The explicit override wins over the default-on project setting, in the off direction.
        System.setProperty(SessionBootstrap.ISOLATED_PROPERTY, "false");
        assertFalse(SessionBootstrap.isolationRequested());
    }

    @Test
    void isolationRequestedWhenPropertyIsTrue() {
        System.setProperty(SessionBootstrap.ISOLATED_PROPERTY, "true");
        assertTrue(SessionBootstrap.isolationRequested());
    }

    @Test
    void launchIsolatedNoOpsWhenNotRequested() {
        // Explicitly opt out (the default is now on) — returns false so the caller runs its normal :0 launch,
        // and never registers a session.
        System.setProperty(SessionBootstrap.ISOLATED_PROPERTY, "false");
        assertFalse(SessionBootstrap.launchIsolated(new LaunchSpec(LaunchKind.EXE, "/bin/true")));
        assertFalse(ActiveSession.isActive());
    }

    @Test
    void backendDefaultsToGamescopeAndHonoursOverride() {
        System.clearProperty(SessionBootstrap.BACKEND_PROPERTY);
        // No override: gamescope, whatever the kind.
        assertEquals(NestedSession.Backend.GAMESCOPE,
                SessionBootstrap.backend(new LaunchSpec(LaunchKind.CLI, "echo hi")));
        assertEquals(NestedSession.Backend.GAMESCOPE,
                SessionBootstrap.backend(new LaunchSpec(LaunchKind.HEROIC, "Firestone")));
        // The explicit override wins over the default (forces Xephyr even for a game).
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
        SessionBackends.DisplaySize size = SessionBootstrap.size();
        assertEquals(SessionBootstrap.DEFAULT_WIDTH, size.width());
        assertEquals(SessionBootstrap.DEFAULT_HEIGHT, size.height());
        // And it says so: a bot that finds nothing needs to be able to tell a display sized to its templates
        // from one sized to a default that matches nothing it captured.
        assertEquals(SessionBackends.SizeSource.FALLBACK, size.source());
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
    void optionsFollowTheDefaultWithoutAnOverride() {
        System.clearProperty(SessionBootstrap.BACKEND_PROPERTY);
        assertEquals(NestedSession.Backend.GAMESCOPE,
                SessionBootstrap.options(new LaunchSpec(LaunchKind.STEAM, "570")).backend());
        assertEquals(NestedSession.Backend.GAMESCOPE,
                SessionBootstrap.options(new LaunchSpec(LaunchKind.CLI, "echo hi")).backend());
    }
}
