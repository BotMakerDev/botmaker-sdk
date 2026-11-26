// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.internal.capture;

import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.capture.Place;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.internal.capture.AutoCapture.Decision;
import com.botmaker.sdk.internal.emulator.EmulatorSource;
import com.botmaker.sdk.internal.web.WebSource;
import com.botmaker.shared.launch.LaunchSpec;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Where a run reads its pixels, from what it is given (spec {@code 2026-11-25-auto-capture-design.md}). */
class AutoCaptureTest {

    private static final LaunchSpec STEAM = LaunchSpec.parse("steam:440");
    private static final LaunchSpec ON_MAIN = LaunchSpec.parse("emu-app:com.foo@Main");
    private static final LaunchSpec NO_INSTANCE = LaunchSpec.parse("emu-app:com.foo");
    private static final Supplier<List<String>> NONE_ASKED = () -> {
        throw new AssertionError("the running instances were asked");
    };

    private static Decision decide(LaunchSpec target, CaptureSource capture) {
        return AutoCapture.decide(new AutoCapture.Inputs(null, target, capture, null, Optional.empty(), NONE_ASKED));
    }

    private static Decision.Fixed fixed(Decision decision) {
        return assertInstanceOf(Decision.Fixed.class, decision);
    }

    private static String instanceOf(Decision decision) {
        return assertInstanceOf(EmulatorSource.class, fixed(decision).source()).instanceName();
    }

    @Test
    void emulatorPropertyWins() {
        Decision decision = AutoCapture.decide(new AutoCapture.Inputs(" Other ", STEAM, CaptureSource.window("c", "t"),
                Place.android("Main"), Optional.empty(), NONE_ASKED));
        assertEquals("Other", instanceOf(decision));
    }

    @Test
    void webTargetReadsItsBrowser() {
        Decision decision = decide(LaunchSpec.parse("web:https://example.com"), CaptureSource.desktop());
        assertInstanceOf(WebSource.class, fixed(decision).source());
    }

    @Test
    void handSetCaptureWins() {
        CaptureSource window = CaptureSource.window("game", "");
        Decision.Fixed fixed = fixed(decide(STEAM, window));
        assertSame(window, fixed.source());
        assertNull(fixed.why(), "a source the author set is not the run's choice");
    }

    @Test
    void desktopWithTargetIsAutomatic() {
        assertInstanceOf(Decision.WatchLaunch.class, decide(STEAM, CaptureSource.desktop()));
        assertTrue(AutoCapture.isAutomatic(CaptureSource.automatic(), null));
        assertTrue(AutoCapture.isAutomatic(new CurrentSource(), STEAM));
    }

    @Test
    void desktopWithoutTargetIsDesktop() {
        assertFalse(AutoCapture.isAutomatic(CaptureSource.desktop(), null));
        assertInstanceOf(Desktop.class, fixed(decide(null, CaptureSource.desktop())).source());
        assertNull(fixed(decide(null, CaptureSource.automatic())).source(), "nothing launched: the whole desktop");
    }

    @Test
    void emuAppTargetReadsItsInstance() {
        Decision decision = decide(ON_MAIN, CaptureSource.automatic());
        assertEquals("Main", instanceOf(decision));
        assertEquals("capture: Main, from the game's target", fixed(decision).why());
        assertEquals("", fixed(decision).playsOn(), "launched where the target says already");
    }

    @Test
    void dealtAndroidPlaceWinsOverTargetInstance() {
        Decision decision = AutoCapture.decide(new AutoCapture.Inputs(null, ON_MAIN, CaptureSource.automatic(),
                Place.android("Second"), Optional.empty(), NONE_ASKED));
        assertEquals("Second", instanceOf(decision));
        assertEquals("Second", fixed(decision).playsOn(), "the game is launched on the dealt instance");
    }

    @Test
    void noInstanceUsesPlaceParam() {
        Decision decision = AutoCapture.decide(new AutoCapture.Inputs(null, NO_INSTANCE, null,
                Place.android("Main"), Optional.empty(), NONE_ASKED));
        assertEquals("Main", instanceOf(decision));
    }

    @Test
    void aPcPlaceDealtLeavesTheTargetInstance() {
        Decision decision = AutoCapture.decide(new AutoCapture.Inputs(null, ON_MAIN, CaptureSource.automatic(),
                Place.desktop(), Optional.empty(), NONE_ASKED));
        assertEquals("Main", instanceOf(decision));
        assertEquals("", fixed(decision).playsOn(), "launched where the target says already");
    }

    @Test
    void noInstanceOneRunning() {
        Decision decision = AutoCapture.decide(new AutoCapture.Inputs(null, NO_INSTANCE, CaptureSource.automatic(),
                null, Optional.empty(), () -> List.of("Only")));
        assertEquals("Only", instanceOf(decision));
        assertEquals("Only", fixed(decision).playsOn());
    }

    @Test
    void noInstanceNoneRunningReadsTheDesktop() {
        // The launch may still start an emulator: the run is not refused before it.
        Decision decision = AutoCapture.decide(new AutoCapture.Inputs(null, NO_INSTANCE, CaptureSource.desktop(),
                null, Optional.empty(), List::of));
        assertNull(fixed(decision).source());
    }

    @Test
    void aRegionOfAutomaticIsTheRegionOfWhatItDecides() {
        Rect top = new Rect(0, 0, 100, 40);
        Decision.Fixed fixed = fixed(decide(ON_MAIN, CaptureSource.automatic().region(top)));
        assertEquals("Main", assertInstanceOf(EmulatorSource.class, fixed.source().base()).instanceName());
        assertEquals(top, fixed.source().subRegion());
        Decision.WatchLaunch watch = assertInstanceOf(Decision.WatchLaunch.class,
                decide(STEAM, CaptureSource.automatic().region(top)));
        assertEquals(List.of(top), watch.regions(), "the region waits for the window the launch opens");
        CaptureSource picked = watch.around(CaptureSource.window("steam_app_440", ""));
        assertEquals(top, picked.subRegion());
        assertInstanceOf(NamedWindow.class, picked.base());
    }

    @Test
    void savedPickMissingWatchesAgain() {
        // What InstalledGame keeps beside a saved pick: the watch the run falls back on when that window is gone.
        Rect top = new Rect(0, 0, 100, 40);
        CaptureSource capture = CaptureSource.automatic().region(top);
        Decision withSaved = AutoCapture.decide(new AutoCapture.Inputs(null, STEAM, capture, null,
                Optional.of(CaptureSource.window("steam_app_440", "")), NONE_ASKED));
        assertInstanceOf(Decision.Fixed.class, withSaved);
        Decision.WatchLaunch again = assertInstanceOf(Decision.WatchLaunch.class, decide(STEAM, capture));
        assertEquals(STEAM, again.target());
        assertEquals(List.of(top), again.regions());
    }

    @Test
    void noInstanceSeveralRunningFails() {
        Decision decision = AutoCapture.decide(new AutoCapture.Inputs(null, NO_INSTANCE, CaptureSource.automatic(),
                null, Optional.empty(), () -> List.of("A", "B")));
        assertEquals("which instance? the target names none and 2 run",
                assertInstanceOf(Decision.Fail.class, decision).message());
    }

    @Test
    void desktopGameSavedPickIsFixed() {
        CaptureSource saved = CaptureSource.window("steam_app_440", "Team Fortress 2");
        Decision decision = AutoCapture.decide(new AutoCapture.Inputs(null, STEAM, CaptureSource.automatic(), null,
                Optional.of(saved), NONE_ASKED));
        assertSame(saved, fixed(decision).source());
        assertTrue(fixed(decision).why().endsWith(", picked on an earlier run"), fixed(decision).why());
    }

    @Test
    void desktopGameNothingSavedWatches() {
        Decision decision = decide(STEAM, CaptureSource.automatic());
        assertEquals(STEAM, assertInstanceOf(Decision.WatchLaunch.class, decision).target());
    }

    @Test
    void onlyAnAutomaticDesktopGameReadsThePick() {
        assertTrue(AutoCapture.wantsSavedWindow(CaptureSource.automatic(), STEAM));
        assertTrue(AutoCapture.wantsSavedWindow(CaptureSource.automatic().region(new Rect(0, 0, 9, 9)), STEAM));
        assertFalse(AutoCapture.wantsSavedWindow(CaptureSource.automatic(), ON_MAIN));
        assertFalse(AutoCapture.wantsSavedWindow(CaptureSource.automatic(), null));
        assertFalse(AutoCapture.wantsSavedWindow(CaptureSource.window("c", ""), STEAM));
    }

    @Test
    void restartUsesSavedPick() {
        // A restart installs again with the pick the first launch saved: it reads it, it does not watch again.
        CaptureSource saved = CaptureSource.window("steam_app_440", "");
        AutoCapture.Inputs again = new AutoCapture.Inputs(null, STEAM, CaptureSource.automatic(), null,
                Optional.of(saved), NONE_ASKED);
        assertSame(saved, fixed(AutoCapture.decide(again)).source());
    }
}
