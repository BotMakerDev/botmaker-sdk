package com.botmaker.sdk.internal.emulator;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** When a run brings the game back in front. */
class GameInFrontTest {

    private static final String GAME = "com.zjcs.android.us";

    @Test
    void theEmulatorsLauncherOrStoreInFrontIsPutBehindTheGame() {
        assertEquals(Optional.of("com.ldmnq.launcher3"),
                GameInFront.cover(Optional.of("com.ldmnq.launcher3"), GAME, Optional::empty));
        assertEquals(Optional.of("com.tencent.launcher"),
                GameInFront.cover(Optional.of("com.tencent.launcher"), GAME, () -> Optional.of("com.tencent.launcher")));
    }

    @Test
    void theGameAnotherAppOrNothingInFrontIsLeftAlone() {
        assertEquals(Optional.empty(), GameInFront.cover(Optional.of(GAME), GAME, () -> {
            throw new AssertionError("the home app is not asked while the game is in front");
        }));
        assertEquals(Optional.empty(), GameInFront.cover(Optional.of("com.google.android.gms"), GAME, Optional::empty));
        assertEquals(Optional.empty(), GameInFront.cover(Optional.empty(), GAME, Optional::empty));
    }
}
