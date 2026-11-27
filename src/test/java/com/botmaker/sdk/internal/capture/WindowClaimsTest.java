// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.internal.capture;

import com.botmaker.shared.capture.GenericWindow;
import com.botmaker.shared.launch.WindowIdentity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/** Two runs of one game on this desktop, each in its own window (run places phase 6). */
class WindowClaimsTest {

    private static final WindowIdentity GAME = WindowIdentity.parse("game.exe");
    private static final GenericWindow FIRST = window(0x3a00007L, "Game - Account 1");
    private static final GenericWindow SECOND = window(0x3c00004L, "Game - Account 2");

    private static GenericWindow window(long id, String title) {
        return new GenericWindow(id, title, new Rectangle(0, 0, 800, 600), 90, "game.exe");
    }

    @AfterEach
    void tearDown() {
        WindowClaims.resetForTesting();
    }

    @Test
    void thePickClaimsItsWindowOnceAndKeepsItWhileItIsOpen() {
        List<String> asked = new ArrayList<>();
        WindowClaims.claimWithForTesting(asked::add, key -> {});

        assertSame(FIRST, WindowClaims.pick(GAME, List.of(FIRST, SECOND)).orElseThrow());
        assertSame(FIRST, WindowClaims.pick(GAME, List.of(SECOND, FIRST)).orElseThrow());
        assertEquals(List.of("window:this-pc/3a00007"), asked, "claimed once, not on every capture");
    }

    @Test
    void aWindowAnotherRunClaimedIsPassedForTheNext() {
        List<String> asked = new ArrayList<>();
        WindowClaims.claimWithForTesting(key -> asked.add(key) && !key.endsWith("3a00007"), key -> {});

        assertSame(SECOND, WindowClaims.pick(GAME, List.of(FIRST, SECOND)).orElseThrow());
        assertEquals(List.of("window:this-pc/3a00007", "window:this-pc/3c00004"), asked);
    }

    @Test
    void everyWindowAnotherRunsGivesNoneAndAClosedPickIsReplacedAndFreed() {
        WindowClaims.claimWithForTesting(key -> false, key -> {});
        assertEquals(Optional.empty(), WindowClaims.pick(GAME, List.of(FIRST, SECOND)));

        List<String> released = new ArrayList<>();
        WindowClaims.claimWithForTesting(key -> true, released::add);
        WindowClaims.pick(GAME, List.of(FIRST));
        assertSame(SECOND, WindowClaims.pick(GAME, List.of(SECOND)).orElseThrow(), "its window closed: pick again");
        assertEquals(List.of("window:this-pc/3a00007"), released);
    }

    @Test
    void aClosedPickIsFreedEvenWhenNoOtherWindowIsOpen() {
        List<String> released = new ArrayList<>();
        WindowClaims.claimWithForTesting(key -> true, released::add);
        WindowClaims.pick(GAME, List.of(FIRST));

        assertEquals(Optional.empty(), WindowClaims.pick(GAME, List.of()));
        assertEquals(List.of("window:this-pc/3a00007"), released, "X11 reuses ids: another run's new window may get it");
        WindowClaims.pick(GAME, List.of());
        assertEquals(1, released.size(), "freed once");
    }
}
