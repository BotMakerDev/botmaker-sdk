// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.internal.capture;

import com.botmaker.sdk.api.bot.BotSettings;
import com.botmaker.sdk.api.capture.Source;
import com.botmaker.sdk.internal.capture.AutoCapture.Decision.WatchLaunch;
import com.botmaker.sdk.internal.capture.NewWindows.Candidate;
import com.botmaker.sdk.internal.session.SessionBootstrap;
import com.botmaker.shared.Diag;
import com.botmaker.shared.capture.GenericWindow;
import com.botmaker.shared.capture.NativeControllerFactory;
import com.botmaker.shared.launch.LaunchSpec;
import com.botmaker.shared.launch.Launcher;
import com.botmaker.shared.launch.RunningProbe;
import com.botmaker.shared.launch.WindowIdentity;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
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
     * How long the recommended window must stay the same one (by handle: a title that counts frames still changes)
     * before it is picked: a splash of its own class, up for a second or two, is replaced by the game first.
     */
    static final Duration STABLE_FOR = Duration.ofSeconds(3);

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
    }

    /**
     * What the watch found: the game's window, the titles of the new windows seen when none was the game's, or a game
     * that runs and could not be closed, whose window would never be a new one.
     */
    sealed interface Result {
        record Picked(WindowIdentity identity, String title) implements Result {}

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
                AutoPicks.run().save(target, picked.identity())
                        .ifPresent(refused -> Diag.warn("Capture", "the window is not kept for the next runs: " + refused));
                Diag.info("Capture", "capture: " + AutoCapture.said(window) + ", the window its launch opened");
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
     * recommended one of {@link NewWindows#ranked}, once the same window stayed recommended for {@link #STABLE_FOR};
     * it is saved as it is titled then.
     */
    static Result watch(LaunchSpec target, Desktop desktop, Duration timeout, Clock clock) {
        if (desktop.running()) {
            Diag.info("Capture", target.spec() + " runs already: closed, so its window is the launch's");
            desktop.close();
            Instant closing = clock.instant().plus(CLOSE_WAIT);
            while (desktop.running() && clock.instant().isBefore(closing)) desktop.pause(POLL);
            if (desktop.running()) return new Result.StillRunning();
        }
        Set<Object> before = NewWindows.handles(desktop.open());
        long launched = desktop.launch();
        Instant deadline = clock.instant().plus(timeout);
        Set<String> seen = new LinkedHashSet<>();
        Object last = null;
        Instant since = null;
        while (clock.instant().isBefore(deadline)) {
            desktop.pause(POLL);
            List<Candidate> ranked = NewWindows.ranked(before, desktop.open(), desktop::launcherUi,
                    pid -> launched > 0 && desktop.inTree(launched, pid));
            for (Candidate candidate : ranked) seen.add(candidate.window().getTitle());
            Candidate best = NewWindows.recommended(ranked);
            Object handle = best == null ? null : best.window().getNativeHandle();
            if (handle == null || !handle.equals(last)) since = clock.instant();
            last = handle;
            if (handle != null && !clock.instant().isBefore(since.plus(STABLE_FOR))) {
                return new Result.Picked(best.identity(), best.window().getTitle());
            }
        }
        return new Result.NoWindow(List.copyOf(seen));
    }

    static String stillRunning(LaunchSpec target) {
        return target.spec() + " runs and cannot be closed from here: close it, so the window its launch opens can be"
                + " picked";
    }

    static String noWindow(LaunchSpec target, List<String> seen) {
        String message = "no game window opened within " + BotSettings.DEFAULT_LAUNCH_WAIT_TIMEOUT / 1000
                + " s of launching " + target.spec();
        return seen.isEmpty() ? message : message + "; new windows seen: " + String.join(", ", seen);
    }

    /** This computer's desktop, for {@code target}. */
    static Desktop host(LaunchSpec target) {
        return new Desktop() {
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
                    Diag.warn("Capture", target.spec() + " cannot be closed from here: close it to pick its window");
                }
            }

            @Override
            public long launch() {
                SessionBootstrap.requireReady(target);
                Process process = Launcher.start(target);
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
