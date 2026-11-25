// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.internal.capture;

import com.botmaker.shared.capture.GenericWindow;
import com.botmaker.shared.launch.LaunchSpec;
import org.junit.jupiter.api.Test;

import java.awt.Rectangle;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/** A run's watch of its game's launch, on a desktop that plays a script. */
class LaunchWatchTest {

    private static final LaunchSpec STEAM = LaunchSpec.parse("steam:440");
    private static final long STEAM_PID = 100;
    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    private static GenericWindow window(long handle, String title, int width, long pid, String windowClass) {
        return new GenericWindow(handle, title, new Rectangle(0, 0, width, width), pid, windowClass);
    }

    /** A clock that moves only when the desktop pauses. */
    private static final class TestClock extends Clock {
        Instant now = Instant.parse("2026-11-25T12:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    /** The windows open before the launch, then those open at each moment after it; the game runs until closed. */
    private static final class FakeDesktop implements LaunchWatch.Desktop {
        final TestClock clock = new TestClock();
        final List<String> calls = new ArrayList<>();
        List<GenericWindow> open;
        final Function<Duration, List<GenericWindow>> afterLaunch;
        boolean running;
        boolean closable = true;
        Instant launchedAt;

        FakeDesktop(List<GenericWindow> before, Function<Duration, List<GenericWindow>> afterLaunch, boolean running) {
            this.open = before;
            this.afterLaunch = afterLaunch;
            this.running = running;
        }

        FakeDesktop(List<GenericWindow> before, List<GenericWindow> afterLaunch, boolean running) {
            this(before, since -> afterLaunch, running);
        }

        @Override
        public List<GenericWindow> open() {
            return launchedAt == null ? open : afterLaunch.apply(Duration.between(launchedAt, clock.now));
        }

        @Override
        public boolean running() {
            return running;
        }

        @Override
        public void close() {
            calls.add("close");
            if (!closable) return;
            running = false;
            open = List.of();
        }

        @Override
        public long launch() {
            calls.add("launch");
            launchedAt = clock.now;
            return -1;
        }

        @Override
        public boolean launcherUi(long pid) {
            return pid == STEAM_PID;
        }

        @Override
        public boolean inTree(long root, long pid) {
            return root == pid;
        }

        @Override
        public void pause(Duration duration) {
            clock.now = clock.now.plus(duration);
        }

        LaunchWatch.Result watch() {
            return LaunchWatch.watch(STEAM, this, TIMEOUT, clock);
        }
    }

    @Test
    void runningGameIsClosedFirst() {
        GenericWindow old = window(1, "Team Fortress 2", 1280, 7, "steam_app_440");
        GenericWindow fresh = window(2, "Team Fortress 2", 1280, 8, "steam_app_440");
        FakeDesktop desktop = new FakeDesktop(List.of(old), List.of(fresh), true);
        LaunchWatch.Result.Picked picked = assertInstanceOf(LaunchWatch.Result.Picked.class, desktop.watch());
        assertEquals(List.of("close", "launch"), desktop.calls);
        assertEquals("steam_app_440", picked.identity().spec());
    }

    /** Its window would never be a new one: the run says to close it rather than wait out the timeout. */
    @Test
    void gameThatCannotBeClosedIsNotLaunched() {
        FakeDesktop desktop = new FakeDesktop(List.of(window(1, "Game", 1280, 7, "game")), List.of(), true);
        desktop.closable = false;
        assertInstanceOf(LaunchWatch.Result.StillRunning.class, desktop.watch());
        assertEquals(List.of("close"), desktop.calls);
    }

    @Test
    void picksNewGameWindow() {
        GenericWindow editor = window(1, "notes.txt - Editor", 900, 50, "gedit");
        FakeDesktop desktop = new FakeDesktop(List.of(editor), List.of(editor,
                window(2, "Steam", 1600, STEAM_PID, "steam"), window(3, "Team Fortress 2", 1280, 8, "steam_app_440")),
                false);
        LaunchWatch.Result.Picked picked = assertInstanceOf(LaunchWatch.Result.Picked.class, desktop.watch());
        assertEquals(List.of("launch"), desktop.calls, "a game not running is not closed");
        assertEquals("Team Fortress 2", picked.title());
    }

    /** A splash of its own class, up for two seconds, then the game: the game is picked. */
    @Test
    void splashOfItsOwnClassIsNotPicked() {
        GenericWindow splash = window(2, "Loading", 400, 8, "UnitySplash");
        GenericWindow game = window(3, "Game", 1280, 8, "game.x86_64");
        FakeDesktop desktop = new FakeDesktop(List.of(),
                since -> since.compareTo(Duration.ofSeconds(2)) < 0 ? List.of(splash) : List.of(game), false);
        assertEquals("game.x86_64",
                assertInstanceOf(LaunchWatch.Result.Picked.class, desktop.watch()).identity().spec());
    }

    /** A generic class is saved with its title, which counts frames: the same window is still picked, as titled then. */
    @Test
    void titleThatChangesIsStillPicked() {
        FakeDesktop desktop = new FakeDesktop(List.of(),
                since -> List.of(window(2, "Game - " + since.toMillis() + " fps", 1280, 8, "wine")), false);
        LaunchWatch.Result.Picked picked = assertInstanceOf(LaunchWatch.Result.Picked.class, desktop.watch());
        assertEquals("wine|" + picked.title(), picked.identity().spec());
    }

    @Test
    void noWindowListsSeen() {
        FakeDesktop desktop = new FakeDesktop(List.of(), List.of(window(2, "Steam", 1600, STEAM_PID, "steam")), false);
        LaunchWatch.Result.NoWindow none = assertInstanceOf(LaunchWatch.Result.NoWindow.class, desktop.watch());
        assertEquals(List.of("Steam"), none.seen());
        assertEquals("no game window opened within 60 s of launching steam:440; new windows seen: Steam",
                LaunchWatch.noWindow(STEAM, none.seen()));
    }

    /** A window open before the launch is never the pick, even when it is the game's class. */
    @Test
    void windowOpenBeforeIsNotPicked() {
        GenericWindow stale = window(1, "Team Fortress 2", 1280, 7, "steam_app_440");
        FakeDesktop desktop = new FakeDesktop(List.of(stale), List.of(stale), false);
        assertInstanceOf(LaunchWatch.Result.NoWindow.class, desktop.watch());
    }
}
