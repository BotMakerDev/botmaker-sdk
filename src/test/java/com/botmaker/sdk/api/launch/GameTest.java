package com.botmaker.sdk.api.launch;

import com.botmaker.sdk.internal.session.SessionBootstrap;
import com.botmaker.shared.capture.GenericWindow;
import com.botmaker.shared.capture.NativeController;
import com.botmaker.shared.launch.LaunchSpec;
import com.botmaker.session.ActiveSession;
import com.botmaker.session.Capability;
import com.botmaker.session.DesktopSession;
import com.botmaker.session.SessionKeyboard;
import com.botmaker.session.SessionPointer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Contract tests for {@link Game}'s argument validation and its background-isolation routing. The validation
 * tests deliberately do not exercise a real launch (that would spawn Steam / a process on the test host) — they
 * only pin the reject-empty-input behavior. The routing tests use a fake {@link ActiveSession}: because
 * {@link SessionBootstrap#launchIsolated} short-circuits on an already-active session, the fake is only ever a
 * non-null marker (its methods are never called), which lets us prove a launch was routed into the private
 * display without a real X server.
 */
class GameTest {

    @AfterEach
    void tearDown() {
        ActiveSession.clear();
        System.clearProperty(SessionBootstrap.ISOLATED_PROPERTY);
    }

    @Test
    void launchRejectsEmptyExecutable() {
        assertThrows(IllegalArgumentException.class, () -> Game.launch(null));
        assertThrows(IllegalArgumentException.class, () -> Game.launch("  "));
    }

    @Test
    void launchSteamRejectsEmptyAppId() {
        assertThrows(IllegalArgumentException.class, () -> Game.launchSteam((String) null));
        assertThrows(IllegalArgumentException.class, () -> Game.launchSteam(""));
        assertThrows(IllegalArgumentException.class, () -> Game.launchSteam("   "));
    }

    @Test
    void launchEpicRejectsEmptyAppId() {
        assertThrows(IllegalArgumentException.class, () -> Game.launchEpic(null));
        assertThrows(IllegalArgumentException.class, () -> Game.launchEpic(""));
        assertThrows(IllegalArgumentException.class, () -> Game.launchEpic("   "));
    }

    @Test
    void launchHeroicRejectsEmptyAppId() {
        assertThrows(IllegalArgumentException.class, () -> Game.launchHeroic(null));
        assertThrows(IllegalArgumentException.class, () -> Game.launchHeroic(""));
        assertThrows(IllegalArgumentException.class, () -> Game.launchHeroic("   "));
    }

    @Test
    void launchFaugusRejectsEmptyGameId() {
        assertThrows(IllegalArgumentException.class, () -> Game.launchFaugus(null));
        assertThrows(IllegalArgumentException.class, () -> Game.launchFaugus(""));
        assertThrows(IllegalArgumentException.class, () -> Game.launchFaugus("   "));
    }

    @Test
    void killAndIsRunningRejectEmptyName() {
        assertThrows(IllegalArgumentException.class, () -> Game.kill(null));
        assertThrows(IllegalArgumentException.class, () -> Game.kill("  "));
        assertThrows(IllegalArgumentException.class, () -> Game.isRunning((String) null));
        assertThrows(IllegalArgumentException.class, () -> Game.isRunning("  "));
    }

    @Test
    void killAndIsRunningAreBestEffortForANonexistentProcess() {
        // A unique, definitely-not-running name: isRunning is false and kill is a quiet no-op (no throw) — the
        // "nothing to kill" case a restart routine relies on. Uses a bogus name so nothing real is affected.
        String bogus = "botmaker-no-such-process-" + System.nanoTime();
        assertFalse(Game.isRunning(bogus), "a made-up process name is not running");
        assertDoesNotThrow(() -> Game.kill(bogus), "killing a nonexistent process must not throw");
    }

    // --- Background-isolation routing ---

    @Test
    void launchRoutesIntoAnActiveSessionInsteadOfTheHost() {
        // Isolation is on by default (no project file opts out) and a session is already active, so the launch
        // is captured by the private display: launch() returns null (no host process) rather than spawning.
        ActiveSession.set(new MarkerSession());
        assertNull(Game.launch("/usr/bin/whatever-game"),
                "an isolated launch is routed into :N and returns no host process handle");
        // The store-kind launches are void; routing means they neither throw nor spawn a host launcher.
        assertDoesNotThrow(() -> Game.launchHeroic("Firestone"));
        assertDoesNotThrow(() -> Game.launchSteam("570"));
    }

    @Test
    void launchFallsToTheHostWhenIsolationIsOff() {
        // Explicitly opt out: no session is brought up, so the host path runs and hands back a real process.
        assumeTrue(new File("/bin/true").canExecute(), "needs a trivial executable to launch on the host");
        System.setProperty(SessionBootstrap.ISOLATED_PROPERTY, "false");
        Process p = Game.launch("/bin/true");
        try {
            org.junit.jupiter.api.Assertions.assertNotNull(p, "a non-isolated launch returns the host process");
        } finally {
            if (p != null) {
                p.destroy();
            }
        }
    }

    /**
     * A non-null {@link DesktopSession} used only as an "active session" marker — {@code launchIsolated}
     * returns early on {@link ActiveSession#isActive()} without touching it, so every method here is
     * unreachable and throws to make an accidental call obvious.
     */
    private static final class MarkerSession implements DesktopSession {
        @Override public Set<Capability> capabilities() { throw new UnsupportedOperationException(); }
        @Override public Rectangle screen() { throw new UnsupportedOperationException(); }
        @Override public SessionPointer pointer() { throw new UnsupportedOperationException(); }
        @Override public SessionKeyboard keyboard() { throw new UnsupportedOperationException(); }
        @Override public void attach(GenericWindow window) { throw new UnsupportedOperationException(); }
        @Override public GenericWindow attached() { throw new UnsupportedOperationException(); }
        @Override public void launch(LaunchSpec spec) { throw new UnsupportedOperationException(); }
        @Override public BufferedImage capture() { throw new UnsupportedOperationException(); }
        @Override public NativeController controller() { throw new UnsupportedOperationException(); }
        @Override public void close() { throw new UnsupportedOperationException(); }
    }
}
