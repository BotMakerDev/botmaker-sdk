// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.internal.capture;

import com.botmaker.sdk.api.bot.BotSettings;
import com.botmaker.sdk.api.capture.Source;
import com.botmaker.sdk.internal.capture.AutoCapture.Decision.WatchLaunch;
import com.botmaker.sdk.internal.capture.NewWindows.Candidate;
import com.botmaker.sdk.internal.observe.RunEvent;
import com.botmaker.sdk.internal.observe.RunFrames;
import com.botmaker.sdk.internal.session.SessionBootstrap;
import com.botmaker.sdk.internal.trace.Say;
import com.botmaker.sdk.internal.trace.SdkRun;
import com.botmaker.shared.capture.GenericWindow;
import com.botmaker.shared.capture.NativeControllerFactory;
import com.botmaker.shared.launch.GameMarker;
import com.botmaker.shared.launch.LaunchSpec;
import com.botmaker.shared.launch.Launcher;
import com.botmaker.shared.launch.RunningProbe;
import com.botmaker.shared.launch.WindowIdentity;

import java.awt.Rectangle;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;

/**
 * A run's watch of its game's launch, when the capture is automatic and no window was picked for this desktop game
 * yet (spec {@code 2026-11-25-auto-capture-design.md}): the game is launched — closed first when it runs already, so
 * its window is a new one — and the window it opens is the one the run reads, and the one kept for the next runs.
 */
public final class LaunchWatch {

    /** How often the open windows are listed while the game comes up. */
    static final Duration POLL = Duration.ofMillis(250);
    /** How long a game asked to close is waited for before it is launched anyway. */
    static final Duration CLOSE_WAIT = Duration.ofSeconds(10);
    /**
     * How long the recommended window must stay the same one (by handle, and by game: a title that counts frames
     * still changes, one that turns from Proton's placeholder into the game's name does not) before it is picked: a
     * splash of its own class, up for a second or two, is replaced by the game first.
     */
    static final Duration STABLE_FOR = Duration.ofSeconds(3);
    /**
     * How long after the launch a window of the game's class open before it may be the pick, when the launch opened
     * none: a game {@code isRunning} missed is only refocused, but a game launched anew opens its window by then.
     */
    static final Duration REFOCUS_AFTER = Duration.ofSeconds(10);
    /**
     * How long a run in doubt waits for the user's pick ({@link NewWindows#doubt}, spec
     * {@code 2026-11-25-window-doubt-picker-design.md}) before it takes its guess and keeps nothing.
     */
    static final Duration ASK_FOR = Duration.ofSeconds(30);
    /** How often a question's answer is read while it is out. */
    private static final Duration ANSWER_POLL = Duration.ofSeconds(1);

    /** The desktop as the watch sees it; the host's is {@link #host}. */
    interface Desktop {
        List<GenericWindow> open();

        boolean running();

        void close();

        /** Launches the game: the process started, or {@code -1} when the launch hands off to a launcher. */
        long launch();

        boolean launcherUi(long pid);

        /** Whether {@code pid} is {@code root} or one of its descendants. */
        boolean inTree(long root, long pid);

        void pause(Duration duration);

        /** Asks whoever is there which of {@code doubt} is {@code target}'s window: false when nobody can answer. */
        default boolean ask(LaunchSpec target, List<Candidate> doubt) {
            return false;
        }

        /** The window picked for {@code target} since it was {@linkplain #ask asked}, or {@code null}. */
        default WindowIdentity answer(LaunchSpec target) {
            return null;
        }

        /** Tells whoever was asked that the question is over. */
        default void settled(LaunchSpec target) {}

        /** Whether {@code pid}'s launcher named the target's game in it ({@code GameMarker}, 2026-11-26). */
        default boolean marked(long pid) {
            return false;
        }

        /**
         * Whether {@code pid} carries this launch's own marker ({@code BOTMAKER_LAUNCH}, 2026-11-26): the launch's,
         * as one of its tree is, though re-parented out of it.
         */
        default boolean ofThisLaunch(long pid) {
            return false;
        }
    }

    /**
     * What the watch found: the game's window, the titles of the new windows seen when none was the game's, or a game
     * that runs and could not be closed, whose window would never be a new one.
     */
    sealed interface Result {
        /** The game's window, and how it was found. */
        record Picked(WindowIdentity identity, String title, How how) implements Result {}

        /** How a window was picked. */
        enum How {
            /** The window the launch opened. */
            OPENED,
            /** No window opened, and the game's own, open before the launch, is the one picked. */
            REFOCUSED,
            /** The watch could not tell, nobody answered: its best guess, kept for no later run. */
            GUESSED,
            /** The user picked it in Studio, which kept it already. */
            BY_YOU
        }

        record NoWindow(List<String> seen) implements Result {}

        record StillRunning() implements Result {}
    }

    private LaunchWatch() {}

    /**
     * Launches {@code watch}'s game and points the run at the window it opens, narrowed as the capture says, keeping it
     * for the next runs ({@link AutoPicks}).
     *
     * @return empty when picked, else why no window was: no game window opened within the launch timeout
     * @throws CancellationException when the run is stopped while it waits
     */
    public static Optional<String> launchAndPick(WatchLaunch watch) {
        LaunchSpec target = watch.target();
        Result result = watch(target, host(target), Duration.ofMillis(BotSettings.DEFAULT_LAUNCH_WAIT_TIMEOUT),
                Clock.systemUTC());
        switch (result) {
            case Result.Picked picked -> {
                NamedWindow window = NamedWindow.of(picked.identity());
                Source.set(watch.around(window));
                String said = "capture: " + AutoCapture.said(window);
                switch (picked.how()) {
                    case OPENED, REFOCUSED -> {
                        AutoPicks.run().save(target, picked.identity())
                                .ifPresent(refused -> Say.warn("the window is not kept for the next runs: " + refused));
                        Say.info(said + (picked.how() == Result.How.REFOCUSED
                                ? ", the game's window its launch refocused" : ", the window its launch opened"));
                    }
                    case GUESSED -> Say.warn(said + ", a guess among the windows its launch opened: nobody picked"
                            + " one, so it is not kept and the next run asks again");
                    case BY_YOU -> Say.info(said + ", picked by you");
                }
                return Optional.empty();
            }
            case Result.NoWindow none -> {
                return Optional.of(noWindow(target, none.seen()));
            }
            case Result.StillRunning ignored -> {
                return Optional.of(stillRunning(target));
            }
        }
    }

    /**
     * Closes {@code target} when it runs, launches it and waits up to {@code timeout} for the window it opens: the
     * recommended one of {@link NewWindows#ranked}, a window of the class the target names first, once the same window
     * stayed recommended for {@link #STABLE_FOR} under the same {@linkplain WindowIdentity#sameGame game}; it is saved
     * as it is titled then. While no new window is recommended, a window of that class open before the launch is
     * waited on the same way ({@link NewWindows#refocused}): a game {@code isRunning} missed is only refocused.
     *
     * <p>A stable pick the ranking cannot tell from other new windows ({@link NewWindows#doubt}) is asked of whoever is
     * there ({@link Desktop#ask}) once; their pick is used as soon as it is written, and with none within
     * {@link #ASK_FOR} the watch takes its guess, which is not kept. With nobody to ask the guess is picked as the
     * launch's window, and kept. A doubt that goes (a window closed) picks as without one.
     */
    static Result watch(LaunchSpec target, Desktop desktop, Duration timeout, Clock clock) {
        if (desktop.running()) {
            Say.info(target.spec() + " runs already: closed, so its window is the launch's");
            desktop.close();
            Instant closing = clock.instant().plus(CLOSE_WAIT);
            while (desktop.running() && clock.instant().isBefore(closing)) desktop.pause(POLL);
            if (desktop.running()) return new Result.StillRunning();
        }
        Set<Object> before = NewWindows.handles(desktop.open());
        long launched = desktop.launch();
        Instant deadline = clock.instant().plus(timeout);
        String gameClass = NewWindows.gameClass(target);
        Set<String> seen = new LinkedHashSet<>();
        Object last = null;
        WindowIdentity lastIdentity = null;
        Instant since = null;
        Instant refocusFrom = clock.instant().plus(REFOCUS_AFTER);
        boolean openedOne = false;
        boolean asked = false;
        Instant answerBy = null;
        Instant nextAnswer = null;
        Candidate guess = null;
        while (clock.instant().isBefore(answerBy == null ? deadline : later(deadline, answerBy.plus(POLL)))) {
            desktop.pause(POLL);
            if (answerBy != null) {
                // The guess at the time out, stable or not now: a window renaming itself meanwhile is no NoWindow.
                if (!clock.instant().isBefore(answerBy)) {
                    desktop.settled(target);
                    return picked(guess, Result.How.GUESSED);
                }
                // Once a second: an answer is this PC's values read again.
                if (!clock.instant().isBefore(nextAnswer)) {
                    nextAnswer = clock.instant().plus(ANSWER_POLL);
                    WindowIdentity answer = desktop.answer(target);
                    if (answer != null) {
                        desktop.settled(target);
                        return new Result.Picked(answer, answer.title(), Result.How.BY_YOU);
                    }
                }
            }
            List<GenericWindow> now = desktop.open();
            List<Candidate> ranked = NewWindows.ranked(before, now, desktop::launcherUi,
                    pid -> launched > 0 && desktop.inTree(launched, pid) || desktop.ofThisLaunch(pid), gameClass,
                    desktop::marked);
            for (Candidate candidate : ranked) seen.add(candidate.window().getTitle());
            Candidate best = NewWindows.recommended(ranked);
            openedOne |= best != null;
            // Only a launch that opened nothing, given time to open one, refocused the game: a window left by a game
            // that crashed is not picked before the new one is up, nor flipped to when a new one closes.
            boolean refocused = best == null && !openedOne && !clock.instant().isBefore(refocusFrom);
            if (refocused) best = NewWindows.refocused(before, now, gameClass);
            Object handle = best == null ? null : best.window().getNativeHandle();
            // A window that renames itself (Proton's placeholder, then the game's name) is waited on again.
            if (handle == null || !handle.equals(last) || !best.identity().sameGame(lastIdentity)) {
                since = clock.instant();
            }
            last = handle;
            lastIdentity = best == null ? null : best.identity();
            if (handle != null && !clock.instant().isBefore(since.plus(STABLE_FOR))) {
                List<Candidate> doubt = refocused ? List.of() : NewWindows.doubt(ranked, gameClass);
                if (doubt.isEmpty()) {
                    if (answerBy != null) desktop.settled(target);
                    return picked(best, refocused ? Result.How.REFOCUSED : Result.How.OPENED);
                }
                if (!asked) {
                    asked = true;
                    // Nobody to ask (a run no host started): the guess is kept, as before there was a question.
                    if (!desktop.ask(target, doubt)) return picked(best, Result.How.OPENED);
                    answerBy = clock.instant().plus(ASK_FOR);
                    nextAnswer = clock.instant().plus(ANSWER_POLL);
                }
                guess = best;
            }
        }
        if (answerBy != null) desktop.settled(target);
        return new Result.NoWindow(List.copyOf(seen));
    }

    private static Result.Picked picked(Candidate window, Result.How how) {
        return new Result.Picked(window.identity(), window.window().getTitle(), how);
    }

    private static Instant later(Instant a, Instant b) {
        return a.isAfter(b) ? a : b;
    }

    static String stillRunning(LaunchSpec target) {
        return target.spec() + " runs and cannot be closed from here: close it, so the window its launch opens can be"
                + " picked";
    }

    /** What a run that watches no launch says when {@code target}'s window is not picked yet: it reads the desktop. */
    public static String unpicked(LaunchSpec target) {
        return "no window is picked for " + target.spec() + " yet: this reads the whole desktop; a run of the bot"
                + " launches the game and picks the window it opens";
    }

    static String noWindow(LaunchSpec target, List<String> seen) {
        String message = "no game window opened within " + BotSettings.DEFAULT_LAUNCH_WAIT_TIMEOUT / 1000
                + " s of launching " + target.spec();
        return seen.isEmpty() ? message : message + "; new windows seen: " + String.join(", ", seen);
    }

    /** This computer's desktop, for {@code target}. */
    static Desktop host(LaunchSpec target) {
        GameMarker launch = GameMarker.launch();
        return new Desktop() {
            // Read on the first window, not before the launch: a Lutris target's name may take its CLI seconds.
            private GameMarker marker;
            private boolean markerRead;

            @Override
            public boolean marked(long pid) {
                if (!markerRead) {
                    marker = GameMarker.of(target);
                    markerRead = true;
                }
                return marker != null && marker.marks(pid);
            }

            @Override
            public boolean ofThisLaunch(long pid) {
                return launch.marks(pid);
            }

            @Override
            public List<GenericWindow> open() {
                try {
                    return NativeControllerFactory.get().getAllWindows();
                } catch (RuntimeException | LinkageError noWindows) {
                    return List.of();
                }
            }

            @Override
            public boolean running() {
                return Launcher.isRunning(target);
            }

            @Override
            public void close() {
                if (!Launcher.stopOnHost(target)) {
                    Say.warn(target.spec() + " cannot be closed from here: close it to pick its window");
                }
            }

            @Override
            public long launch() {
                SessionBootstrap.requireReady(target);
                Process process = launch.during(() -> Launcher.start(target));
                return process == null ? -1 : process.pid();
            }

            @Override
            public boolean launcherUi(long pid) {
                return RunningProbe.launcherUi(pid);
            }

            @Override
            public boolean inTree(long root, long pid) {
                Optional<ProcessHandle> process = ProcessHandle.of(pid);
                for (int depth = 0; process.isPresent() && depth < 64; depth++) {
                    if (process.get().pid() == root) return true;
                    process = process.get().parent();
                }
                return false;
            }

            @Override
            public boolean ask(LaunchSpec game, List<Candidate> doubt) {
                // Only a run a host started has a Studio that may hear its frames and write the pick.
                if (!SdkRun.hosted()) return false;
                // A pick kept still is one this launch did not open (the watch runs again over it): forgotten, so a
                // pick of the same window in Studio is a write the run sees.
                if (AutoPicks.run().picked(game).isPresent() && AutoPicks.run().forget(game).isPresent()) return false;
                List<RunEvent.DoubtfulWindow> windows = new ArrayList<>();
                for (Candidate candidate : doubt.subList(0, Math.min(doubt.size(), RunFrames.MAX_WINDOWS))) {
                    GenericWindow window = candidate.window();
                    Rectangle rect = window.getRect();
                    windows.add(new RunEvent.DoubtfulWindow(String.valueOf(window.getNativeHandle()),
                            window.getTitle(), candidate.identity().spec(), rect == null ? RunEvent.NO_SURFACE
                            : new RunEvent.Target(window.getTitle(), rect.x, rect.y, rect.width, rect.height)));
                }
                if (!send(new RunEvent.WindowDoubt(game.spec(), (int) ASK_FOR.toSeconds(), windows))) return false;
                Say.info(doubt.size() + " new windows could be " + game.spec() + "'s: asking in Studio which, for "
                        + ASK_FOR.toSeconds() + " s at most");
                return true;
            }

            @Override
            public WindowIdentity answer(LaunchSpec game) {
                return AutoPicks.run().picked(game).orElse(null);
            }

            @Override
            public void settled(LaunchSpec game) {
                send(new RunEvent.WindowSettled(game.spec()));
            }

            private boolean send(RunEvent event) {
                try {
                    SdkRun.channel().frame(RunFrames.encode(event));
                    return true;
                } catch (UncheckedIOException tooLong) {
                    return false;
                }
            }

            @Override
            public void pause(Duration duration) {
                try {
                    Thread.sleep(duration);
                } catch (InterruptedException stopped) {
                    Thread.currentThread().interrupt();
                    throw new CancellationException("stopped while waiting for the game's window");
                }
            }
        };
    }
}
