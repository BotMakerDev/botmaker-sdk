package com.botmaker.sdk.api.launch;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Contract tests for {@link Game}'s argument validation. These deliberately do not exercise a real launch
 * (that would spawn Steam / a process on the test host) — they only pin the reject-empty-input behavior that
 * guards both {@code launch} and {@code launchSteam} (including the numeric {@code launchSteam(int)} overload,
 * which delegates to the String form).
 */
class GameTest {

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
}
