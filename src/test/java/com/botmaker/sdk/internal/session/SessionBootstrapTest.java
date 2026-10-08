package com.botmaker.sdk.internal.session;

import com.botmaker.sdk.api.bot.BotSettings;
import com.botmaker.sdk.internal.bot.Session;
import com.botmaker.shared.launch.LaunchKind;
import com.botmaker.shared.launch.LaunchSpec;
import com.botmaker.shared.launch.RunState;
import com.botmaker.session.display.SessionBackends;
import com.botmaker.session.SessionBackend;
import com.botmaker.shared.platform.Os;
import com.botmaker.shared.tools.UserDirs;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The bot-runtime producer's gate and backend/size selection — the pure part that decides <em>whether</em> and
 * <em>how</em> to go isolated. The live bring-up ({@code Sessions.startPrivate} → launch → register) needs a real
 * X server and is verified by the shared live suite / manually, exactly like Studio's launcher.
 */
class SessionBootstrapTest {

    @AfterEach
    void tearDown() {
        System.clearProperty(SessionBootstrap.WHERE_PROPERTY);
        System.clearProperty(SessionBootstrap.BACKEND_PROPERTY);
        System.clearProperty(SessionBootstrap.VM_PROPERTY);
        // Session's overrides are static and outrank everything below them — a leak would silently pin every
        // later test in this JVM.
        Session.clearOverrides();
        BotSession.clear();
    }

    @Test
    @EnabledOnOs(OS.LINUX) // isolated means a private display, which only Linux has
    void anExplicitSessionCallOutranksTheSystemProperty() {
        // The top rung of the ladder: bot code must be able to force its own behaviour on a machine whose
        // environment says the opposite, in both directions.
        System.setProperty(SessionBootstrap.WHERE_PROPERTY, "my-desktop");
        Session.enable();
        assertTrue(SessionBootstrap.isolationRequested());

        System.setProperty(SessionBootstrap.WHERE_PROPERTY, "private-display");
        Session.disable();
        assertFalse(SessionBootstrap.isolationRequested());
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void isEnabledReportsTheResolvedAnswerNotJustWhatBotCodeAsked() {
        // Session.isEnabled() is the whole ladder, so a bot that never calls anything still reads the truth.
        System.setProperty(SessionBootstrap.WHERE_PROPERTY, "my-desktop");
        assertFalse(Session.isEnabled());
        Session.enable();
        assertTrue(Session.isEnabled());
    }

    @Test
    void useBackendOutranksThePropertyAndAutoUnpinsToTheRungBelow() {
        System.setProperty(SessionBootstrap.BACKEND_PROPERTY, "xephyr");
        Session.useBackend("gamescope");
        assertEquals(SessionBackend.GAMESCOPE,
                SessionBootstrap.backend(new LaunchSpec(LaunchKind.CLI, "echo hi")));

        // "auto" is not a backend: it un-pins, dropping to the next rung (here, the xephyr property).
        Session.useBackend("auto");
        assertEquals(SessionBackend.XEPHYR,
                SessionBootstrap.backend(new LaunchSpec(LaunchKind.HEROIC, "Firestone")));
    }

    @Test
    void autoAndTyposDoNotSilentlyPinXephyr() {
        // The regression this ladder's total parse fixes: `session.backend=auto` (and any typo) used to hit
        // `"gamescope".equalsIgnoreCase(x) ? GAMESCOPE : XEPHYR` and pin a game to Xephyr's software GL — the
        // exact crash the kind-driven choice exists to prevent. Both must fall through to the kind.
        for (String value : new String[]{"auto", "gamescpoe", ""}) {
            System.setProperty(SessionBootstrap.BACKEND_PROPERTY, value);
            assertEquals(SessionBackend.GAMESCOPE,
                    SessionBootstrap.backend(new LaunchSpec(LaunchKind.HEROIC, "Firestone")),
                    "a game must still get gamescope with session.backend='" + value + "'");
        }
    }

    @Test
    void eachPlaceIsOffTheDesktopOnlyWhereThisComputerHasIt() {
        assertTrue(SessionBootstrap.isolatedOn(BotSettings.Where.PRIVATE_DISPLAY, Os.LINUX));
        assertFalse(SessionBootstrap.isolatedOn(BotSettings.Where.PRIVATE_DISPLAY, Os.WINDOWS));
        assertTrue(SessionBootstrap.isolatedOn(BotSettings.Where.VM, Os.WINDOWS));
        assertFalse(SessionBootstrap.isolatedOn(BotSettings.Where.VM, Os.LINUX));
        assertFalse(SessionBootstrap.isolatedOn(BotSettings.Where.MY_DESKTOP, Os.WINDOWS));
    }

    @Test
    void thePropertyNamesTheVmAndBotCodeOutranksIt() {
        System.setProperty(SessionBootstrap.WHERE_PROPERTY, "vm");
        assertEquals(BotSettings.Where.VM, SessionBootstrap.where());
        Session.disable();
        assertEquals(BotSettings.Where.MY_DESKTOP, SessionBootstrap.where());
        Session.enable();
        assertEquals(BotSettings.Where.PRIVATE_DISPLAY, SessionBootstrap.where(),
                "on is the settings' own isolated place, a private display by default");
        assertEquals(BotSettings.Where.VM, SessionBootstrap.where(BotSettings.Where.VM, Os.WINDOWS));
        assertEquals(BotSettings.Where.PRIVATE_DISPLAY, SessionBootstrap.where(BotSettings.Where.VM, Os.LINUX),
                "on Linux, on is a private display whatever the settings say");
    }

    @Test
    void theVmIsTheOneNamedElseTheOnlyOneSetUp(@TempDir Path config) {
        System.setProperty(SessionBootstrap.VM_PROPERTY, " live ");
        assertEquals("live", SessionBootstrap.vmName());

        System.clearProperty(SessionBootstrap.VM_PROPERTY);
        String before = System.getProperty(UserDirs.CONFIG_PROPERTY);
        System.setProperty(UserDirs.CONFIG_PROPERTY, config.toString());
        try {
            IllegalStateException none = assertThrows(IllegalStateException.class, SessionBootstrap::vmName);
            assertTrue(none.getMessage().contains("no game VM"), none.getMessage());
            assertTrue(none.getMessage().contains("My desktop"), none.getMessage());
        } finally {
            if (before == null) System.clearProperty(UserDirs.CONFIG_PROPERTY);
            else System.setProperty(UserDirs.CONFIG_PROPERTY, before);
        }
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void isolationIsOnByDefault() {
        // No settings installed → BotSettings.DEFAULTS → a private display.
        System.clearProperty(SessionBootstrap.WHERE_PROPERTY);
        assertTrue(SessionBootstrap.isolationRequested());
    }

    @Test
    void systemPropertyOverridesToOff() {
        // The explicit override wins over the default-on project setting, in the off direction.
        System.setProperty(SessionBootstrap.WHERE_PROPERTY, "my-desktop");
        assertFalse(SessionBootstrap.isolationRequested());
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void isolationRequestedWhenPropertyIsTrue() {
        System.setProperty(SessionBootstrap.WHERE_PROPERTY, "private-display");
        assertTrue(SessionBootstrap.isolationRequested());
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void aPropertyThatNamesNoPlaceLeavesTheSettingsInCharge() {
        // The old boolean property is not a place: it must not be read as one, in either direction.
        System.setProperty(SessionBootstrap.WHERE_PROPERTY, "false");
        assertTrue(SessionBootstrap.isolationRequested());
    }

    @Test
    void aPinnedDisplayBackendWinsOverTheKindsChoice() {
        LaunchSpec game = new LaunchSpec(LaunchKind.STEAM, "570");
        assertEquals(SessionBackend.GAMESCOPE,
                SessionBootstrap.backendFor(game, BotSettings.DisplayBackend.AUTO));
        assertEquals(SessionBackend.XEPHYR,
                SessionBootstrap.backendFor(game, BotSettings.DisplayBackend.XEPHYR));
    }

    @Test
    void aMissingBackendSaysHowToGetItAndTheWayAround() {
        String why = SessionBootstrap.missingBackend(SessionBackend.GAMESCOPE);
        assertTrue(why.startsWith("gamescope isn't installed"), why);
        assertTrue(why.contains("My desktop"), why);
    }

    @Test
    void launchIsolatedNoOpsWhenNotRequested() {
        // Explicitly opt out (the default is now on) — returns false so the caller runs its normal :0 launch,
        // and never registers a session.
        System.setProperty(SessionBootstrap.WHERE_PROPERTY, "my-desktop");
        assertFalse(SessionBootstrap.launchIsolated(new LaunchSpec(LaunchKind.EXE, "/bin/true")));
        assertFalse(BotSession.isActive());
    }

    @Test
    void backendDefaultsToGamescopeAndHonoursOverride() {
        System.clearProperty(SessionBootstrap.BACKEND_PROPERTY);
        // No override: gamescope, whatever the kind.
        assertEquals(SessionBackend.GAMESCOPE,
                SessionBootstrap.backend(new LaunchSpec(LaunchKind.CLI, "echo hi")));
        assertEquals(SessionBackend.GAMESCOPE,
                SessionBootstrap.backend(new LaunchSpec(LaunchKind.HEROIC, "Firestone")));
        // The explicit override wins over the default (forces Xephyr even for a game).
        System.setProperty(SessionBootstrap.BACKEND_PROPERTY, "xephyr");
        assertEquals(SessionBackend.XEPHYR,
                SessionBootstrap.backend(new LaunchSpec(LaunchKind.HEROIC, "Firestone")));
        System.setProperty(SessionBootstrap.BACKEND_PROPERTY, "gamescope");
        assertEquals(SessionBackend.GAMESCOPE,
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
        com.botmaker.session.SessionOptions o = SessionBootstrap.options(new LaunchSpec(LaunchKind.CLI, "echo hi"));
        assertEquals(SessionBackend.GAMESCOPE, o.backend());
        assertEquals(SessionBootstrap.DEFAULT_WIDTH, o.width());
        assertEquals(SessionBootstrap.DEFAULT_HEIGHT, o.height());
    }

    @Test
    void aRecoveryDoesNotStartAgainAVmTheUserShutDown() {
        java.util.List<LaunchSpec> launched = new java.util.ArrayList<>();
        BotSession.set(new com.botmaker.session.DesktopSession() {
            @Override public java.util.Set<com.botmaker.session.Capability> capabilities() { return java.util.Set.of(); }
            @Override public java.awt.Rectangle screen() { return new java.awt.Rectangle(); }
            @Override public String displayName() { return "VM game"; }
            @Override public void attach(com.botmaker.shared.capture.GenericWindow window) { }
            @Override public com.botmaker.shared.capture.GenericWindow attached() { return null; }
            @Override public void launch(LaunchSpec spec) { launched.add(spec); }
            @Override public java.awt.image.BufferedImage capture() { return null; }
            @Override public com.botmaker.shared.capture.NativeController controller() { return null; }
            @Override public com.botmaker.session.SessionHealth health() { return com.botmaker.session.SessionHealth.DEAD; }
            @Override public java.util.Optional<String> endedBecause() {
                return java.util.Optional.of("The game VM game was shut down (Windows shut down).");
            }
            @Override public void close() { }
        });
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> SessionBootstrap.relaunchInVm(new LaunchSpec(LaunchKind.STEAM, "570")));
        assertEquals("The game VM game was shut down (Windows shut down). Run the bot again to start it.", e.getMessage());
        assertTrue(launched.isEmpty());
        assertFalse(SessionBootstrap.vmAlive());
    }

    @Test
    void aRecoveryEndsTheGameInTheVmThenStartsItAndTheGuestSaysWhetherItRuns() {
        java.util.List<String> did = new java.util.ArrayList<>();
        java.util.concurrent.atomic.AtomicReference<RunState> state = new java.util.concurrent.atomic.AtomicReference<>();
        BotSession.set(new com.botmaker.session.DesktopSession() {
            @Override public java.util.Set<com.botmaker.session.Capability> capabilities() { return java.util.Set.of(); }
            @Override public java.awt.Rectangle screen() { return new java.awt.Rectangle(); }
            @Override public String displayName() { return "VM game"; }
            @Override public void attach(com.botmaker.shared.capture.GenericWindow window) { }
            @Override public com.botmaker.shared.capture.GenericWindow attached() { return null; }
            @Override public void launch(LaunchSpec spec) { did.add("launch " + spec.spec()); }
            @Override public RunState running(LaunchSpec spec) { return state.get(); }
            @Override public boolean stop(LaunchSpec spec) { did.add("stop " + spec.spec()); return true; }
            @Override public java.awt.image.BufferedImage capture() { return null; }
            @Override public com.botmaker.shared.capture.NativeController controller() { return null; }
            @Override public void close() { }
        });
        LaunchSpec game = new LaunchSpec(LaunchKind.EXE, "C:\\Games\\g.exe");
        SessionBootstrap.relaunchInVm(game);
        assertEquals(java.util.List.of("stop exe:C:\\Games\\g.exe", "launch exe:C:\\Games\\g.exe"), did);

        state.set(RunState.RUNNING);
        assertTrue(SessionBootstrap.runningInVm(game));
        state.set(RunState.STOPPED);
        assertFalse(SessionBootstrap.runningInVm(game));
        state.set(RunState.UNKNOWN);
        assertTrue(SessionBootstrap.runningInVm(game), "the guest can't tell: the VM is open");
        BotSession.clear();
        assertFalse(SessionBootstrap.runningInVm(game), "no VM opened yet");
    }

    @Test
    void optionsFollowTheDefaultWithoutAnOverride() {
        System.clearProperty(SessionBootstrap.BACKEND_PROPERTY);
        assertEquals(SessionBackend.GAMESCOPE,
                SessionBootstrap.options(new LaunchSpec(LaunchKind.STEAM, "570")).backend());
        assertEquals(SessionBackend.GAMESCOPE,
                SessionBootstrap.options(new LaunchSpec(LaunchKind.CLI, "echo hi")).backend());
    }
}
