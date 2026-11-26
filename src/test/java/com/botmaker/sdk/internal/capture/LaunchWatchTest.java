// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.internal.capture;

import com.botmaker.shared.capture.GenericWindow;
import com.botmaker.shared.launch.LaunchSpec;
import com.botmaker.shared.launch.WindowIdentity;
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
import java.util.function.LongPredicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

        /** The processes the target's launcher named; none by default. */
        LongPredicate marked = pid -> false;

        @Override
        public boolean marked(long pid) {
            return marked.test(pid);
        }

        /** The processes that carry this launch's own marker; none by default. */
        LongPredicate ofThisLaunch = pid -> false;

        @Override
        public boolean ofThisLaunch(long pid) {
            return ofThisLaunch.test(pid);
        }

        /** Whether someone is there to answer a doubt. */
        boolean canAsk;
        /** What they have picked, by the time since they were asked; null: nothing yet. */
        Function<Duration, WindowIdentity> picks = since -> null;
        final List<List<NewWindows.Candidate>> asked = new ArrayList<>();
        Instant askedAt;
        int settled;

        @Override
        public boolean ask(LaunchSpec target, List<NewWindows.Candidate> doubt) {
            asked.add(doubt);
            askedAt = clock.now;
            return canAsk;
        }

        @Override
        public WindowIdentity answer(LaunchSpec target) {
            return askedAt == null ? null : picks.apply(Duration.between(askedAt, clock.now));
        }

        @Override
        public void settled(LaunchSpec target) {
            settled++;
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

    /** Proton's window titled with its own class, then the game's name: picked as the game, never before. */
    @Test
    void protonsPlaceholderIsWaitedOutUnderTheGamesName() {
        FakeDesktop desktop = new FakeDesktop(List.of(), since -> List.of(window(2,
                since.compareTo(Duration.ofSeconds(5)) < 0 ? "steam_proton" : "Balatro", 1280, 8, "steam_proton")),
                false);
        LaunchWatch.Result.Picked picked = assertInstanceOf(LaunchWatch.Result.Picked.class, desktop.watch());
        assertEquals("steam_proton|Balatro", picked.identity().spec());
    }

    /** A generic window's new name restarts the wait: a name is saved only once it held for the whole wait. */
    @Test
    void aWindowThatRenamesItselfIsWaitedOnAgain() {
        FakeDesktop desktop = new FakeDesktop(List.of(), since -> List.of(window(2,
                since.compareTo(Duration.ofMillis(2900)) < 0 ? "Loading" : "Balatro", 1280, 8, "wine")), false);
        assertEquals("wine|Balatro",
                assertInstanceOf(LaunchWatch.Result.Picked.class, desktop.watch()).identity().spec());
        assertTrue(Duration.between(desktop.launchedAt, desktop.clock.now)
                .compareTo(Duration.ofMillis(2900).plus(LaunchWatch.STABLE_FOR)) >= 0, "picked before its name settled");
    }

    @Test
    void noWindowListsSeen() {
        FakeDesktop desktop = new FakeDesktop(List.of(), List.of(window(2, "Steam", 1600, STEAM_PID, "steam")), false);
        LaunchWatch.Result.NoWindow none = assertInstanceOf(LaunchWatch.Result.NoWindow.class, desktop.watch());
        assertEquals(List.of("Steam"), none.seen());
        assertEquals("no game window opened within 60 s of launching steam:440; new windows seen: Steam",
                LaunchWatch.noWindow(STEAM, none.seen()));
    }

    /** A window open before the launch, of no class the target names, is never the pick. */
    @Test
    void windowOpenBeforeIsNotPicked() {
        GenericWindow stale = window(1, "Hades II", 1280, 7, "hades2.x86_64");
        FakeDesktop desktop = new FakeDesktop(List.of(stale), List.of(stale), false);
        assertInstanceOf(LaunchWatch.Result.NoWindow.class, desktop.watch());
    }

    /** The game ran though {@code isRunning} missed it: the launch only refocuses its window, which is picked. */
    @Test
    void aLaunchThatOnlyRefocusesPicksTheGamesOpenWindow() {
        GenericWindow game = window(1, "Team Fortress 2", 1280, 7, "steam_app_440");
        GenericWindow other = window(2, "Honkai: Star Rail", 1920, 9, "steam_app_1965");
        FakeDesktop desktop = new FakeDesktop(List.of(game, other), List.of(game, other), false);
        LaunchWatch.Result.Picked picked = assertInstanceOf(LaunchWatch.Result.Picked.class, desktop.watch());
        assertEquals("steam_app_440", picked.identity().spec());
        assertEquals(LaunchWatch.Result.How.REFOCUSED, picked.how());
    }

    /** A window the launch opens wins over the game's class open before: it is the launch's. */
    @Test
    void aNewWindowWinsOverTheRefocusedOne() {
        GenericWindow old = window(1, "Team Fortress 2", 1280, 7, "steam_app_440");
        GenericWindow fresh = window(3, "Team Fortress 2 - Launcher", 800, 8, "tf_launcher");
        FakeDesktop desktop = new FakeDesktop(List.of(old), List.of(old, fresh), false);
        LaunchWatch.Result.Picked picked = assertInstanceOf(LaunchWatch.Result.Picked.class, desktop.watch());
        assertEquals("tf_launcher", picked.identity().spec());
        assertEquals(LaunchWatch.Result.How.OPENED, picked.how());
    }

    /** A crashed game's window is left open; the new game's window comes up 8 s after the launch: that one is picked. */
    @Test
    void aWindowLeftByACrashLosesToTheOneTheLaunchOpens() {
        GenericWindow left = window(1, "Team Fortress 2", 1280, 7, "steam_app_440");
        GenericWindow fresh = window(3, "Team Fortress 2", 1280, 8, "steam_app_440");
        FakeDesktop desktop = new FakeDesktop(List.of(left), since -> since.compareTo(Duration.ofSeconds(8)) < 0
                ? List.of(left) : List.of(left, fresh), false);
        LaunchWatch.Result.Picked picked = assertInstanceOf(LaunchWatch.Result.Picked.class, desktop.watch());
        assertEquals(LaunchWatch.Result.How.OPENED, picked.how());
    }

    /**
     * Two Wine windows, neither the launch's own: Lutris's case, which only size tells apart. The guess is a little
     * bigger, so it is the guess whichever order the desktop lists them in.
     */
    private static FakeDesktop twoWineWindows() {
        return new FakeDesktop(List.of(), List.of(window(2, "Balatro", 1300, 8, "wine"),
                window(3, "Battle.net", 1280, 9, "wine")), false);
    }

    /** In doubt, the watch asks once and uses the pick written meanwhile, which Studio kept already. */
    @Test
    void aDoubtIsAskedAndThePickWrittenMeanwhileIsUsed() {
        FakeDesktop desktop = twoWineWindows();
        desktop.canAsk = true;
        desktop.picks = since -> since.compareTo(Duration.ofSeconds(5)) < 0 ? null
                : WindowIdentity.of("wine", "Battle.net");
        LaunchWatch.Result.Picked picked = assertInstanceOf(LaunchWatch.Result.Picked.class, desktop.watch());
        assertEquals(1, desktop.asked.size());
        assertEquals(List.of("wine|Balatro", "wine|Battle.net"),
                desktop.asked.getFirst().stream().map(c -> c.identity().spec()).toList(), "the guess first");
        assertEquals("wine|Battle.net", picked.identity().spec());
        assertEquals(LaunchWatch.Result.How.BY_YOU, picked.how());
        assertEquals(1, desktop.settled, "the window is told the question is over");
    }

    /** Nobody answers: the guess after {@code ASK_FOR}, past the launch's own timeout, and Studio's window closed. */
    @Test
    void anUnansweredDoubtTakesTheGuessAfterAskFor() {
        FakeDesktop desktop = twoWineWindows();
        desktop.canAsk = true;
        LaunchWatch.Result.Picked picked = assertInstanceOf(LaunchWatch.Result.Picked.class, desktop.watch());
        assertEquals("wine|Balatro", picked.identity().spec());
        assertEquals(LaunchWatch.Result.How.GUESSED, picked.how());
        assertTrue(Duration.between(desktop.askedAt, desktop.clock.now).compareTo(LaunchWatch.ASK_FOR) >= 0);
        assertEquals(1, desktop.settled);
    }

    /**
     * Nobody to ask (no host started the run): the guess at once, kept as the launch's window as before there was a
     * question, so a run from the command line does not close and relaunch the game every time.
     */
    @Test
    void aDoubtWithNobodyToAskKeepsTheGuessAtOnce() {
        FakeDesktop desktop = twoWineWindows();
        LaunchWatch.Result.Picked picked = assertInstanceOf(LaunchWatch.Result.Picked.class, desktop.watch());
        assertEquals("wine|Balatro", picked.identity().spec());
        assertEquals(LaunchWatch.Result.How.OPENED, picked.how());
        assertEquals(1, desktop.asked.size());
        assertTrue(Duration.between(desktop.launchedAt, desktop.clock.now)
                .compareTo(LaunchWatch.STABLE_FOR.plus(Duration.ofSeconds(1))) < 0, "waited as if asked");
        assertEquals(0, desktop.settled, "nothing was asked, so nothing is settled");
    }

    /**
     * The guess renames itself just as the wait ends, so it is not stable then: still the guess, never NoWindow, which
     * would stop the run.
     */
    @Test
    void aGuessRenamingAtTheTimeOutIsStillTheGuess() {
        Duration renames = LaunchWatch.STABLE_FOR.plus(LaunchWatch.ASK_FOR);
        FakeDesktop desktop = new FakeDesktop(List.of(), since -> List.of(
                window(2, since.compareTo(renames) < 0 ? "Balatro" : "Balatro (1)", 1300, 8, "wine"),
                window(3, "Battle.net", 1280, 9, "wine")), false);
        desktop.canAsk = true;
        LaunchWatch.Result.Picked picked = assertInstanceOf(LaunchWatch.Result.Picked.class, desktop.watch());
        assertEquals(LaunchWatch.Result.How.GUESSED, picked.how());
        assertEquals(1, desktop.settled);
    }

    /** One of the two closes while the question is out: the other is picked as the launch's, and the window closed. */
    @Test
    void aDoubtThatGoesPicksTheWindowLeft() {
        GenericWindow balatro = window(2, "Balatro", 1300, 8, "wine");
        GenericWindow other = window(3, "Battle.net", 1280, 9, "wine");
        FakeDesktop desktop = new FakeDesktop(List.of(), since -> since.compareTo(Duration.ofSeconds(8)) < 0
                ? List.of(balatro, other) : List.of(balatro), false);
        desktop.canAsk = true;
        LaunchWatch.Result.Picked picked = assertInstanceOf(LaunchWatch.Result.Picked.class, desktop.watch());
        assertEquals("wine|Balatro", picked.identity().spec());
        assertEquals(LaunchWatch.Result.How.OPENED, picked.how());
        assertEquals(1, desktop.settled);
    }

    /** A Steam game's own class decides: no question. */
    @Test
    void theGamesClassLeavesNoDoubt() {
        FakeDesktop desktop = new FakeDesktop(List.of(), List.of(window(2, "Team Fortress 2", 1280, 8, "steam_app_440"),
                window(3, "Battle.net", 1920, 9, "wine")), false);
        desktop.canAsk = true;
        LaunchWatch.Result.Picked picked = assertInstanceOf(LaunchWatch.Result.Picked.class, desktop.watch());
        assertEquals("steam_app_440", picked.identity().spec());
        assertTrue(desktop.asked.isEmpty());
    }

    /** The window whose process the launcher named is the game's, though smaller: no question. */
    @Test
    void theProcessItsLauncherNamedLeavesNoDoubt() {
        FakeDesktop desktop = twoWineWindows();
        desktop.canAsk = true;
        desktop.marked = pid -> pid == 9;
        LaunchWatch.Result.Picked picked = assertInstanceOf(LaunchWatch.Result.Picked.class, desktop.watch());
        assertEquals("wine|Battle.net", picked.identity().spec());
        assertEquals(LaunchWatch.Result.How.OPENED, picked.how());
        assertTrue(desktop.asked.isEmpty());
    }

    /**
     * A launch handed off (no process of its own), whose game kept the launch's marker: that window is the launch's,
     * as one of its tree is, and leaves no doubt.
     */
    @Test
    void theProcessCarryingTheLaunchsMarkerLeavesNoDoubt() {
        FakeDesktop desktop = twoWineWindows();
        desktop.canAsk = true;
        desktop.ofThisLaunch = pid -> pid == 9;
        LaunchWatch.Result.Picked picked = assertInstanceOf(LaunchWatch.Result.Picked.class, desktop.watch());
        assertEquals("wine|Battle.net", picked.identity().spec());
        assertTrue(desktop.asked.isEmpty());
    }

    /** A popup the launch opened, then closed: the watch does not fall back to the old window. */
    @Test
    void aNewWindowThatClosedIsNoReasonToRefocus() {
        GenericWindow old = window(1, "Team Fortress 2", 1280, 7, "steam_app_440");
        GenericWindow popup = window(3, "Preparing to launch", 400, 8, "steam_popup");
        FakeDesktop desktop = new FakeDesktop(List.of(old), since -> since.compareTo(Duration.ofSeconds(2)) < 0
                ? List.of(old, popup) : List.of(old), false);
        assertInstanceOf(LaunchWatch.Result.NoWindow.class, desktop.watch());
    }

    @Test
    void aRunThatWatchesNoLaunchSaysItReadsTheDesktop() {
        assertEquals("no window is picked for steam:440 yet: this reads the whole desktop; a run of the bot launches"
                + " the game and picks the window it opens", LaunchWatch.unpicked(STEAM));
    }
}
