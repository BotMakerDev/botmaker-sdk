package com.botmaker.sdk.plugin.launch;

import com.botmaker.plugin.api.run.Readiness;
import com.botmaker.shared.launch.GameReadiness;
import com.botmaker.shared.launch.LaunchSpec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** The Run window's readiness line, from the bot's launch target and capture window (2026-10-25). */
class GameReadyTest {

    private static final LaunchSpec STEAM = LaunchSpec.parse("steam:570");

    @Test
    void anOpenWindowIsReadyWhateverTheLauncherSays() {
        assertEquals(Readiness.ready("game window found"), GameReady.of(STEAM, null, true, true));
    }

    @Test
    void aClosedWindowWithNothingToLaunchIsBlocked() {
        assertEquals(Readiness.Level.BLOCKED, GameReady.of(null, null, true, false).level());
    }

    @Test
    void nothingToLaunchAndNoWindowSaysNothing() {
        assertNull(GameReady.of(null, null, false, false));
    }

    @Test
    void theLauncherDecidesWhenTheWindowIsClosed() {
        assertEquals(Readiness.Level.READY, GameReady.of(STEAM, GameReadiness.ready(), true, false).level());
        assertEquals(Readiness.waiting("Steam is updating it."),
                GameReady.of(STEAM, new GameReadiness(GameReadiness.State.UPDATING, "Steam is updating it."),
                        true, false));
        assertEquals(Readiness.blocked("Signed out of Steam."),
                GameReady.of(STEAM, new GameReadiness(GameReadiness.State.SIGNED_OUT, "Signed out of Steam."),
                        false, false));
        assertEquals(Readiness.Level.BLOCKED, GameReady.of(STEAM,
                new GameReadiness(GameReadiness.State.NOT_INSTALLED, "Not installed."), false, false).level());
    }
}
