// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.internal.explore;

import com.botmaker.sdk.internal.explore.ExploreLoop.ScreenBudget;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongSupplier;

/**
 * Each screen's own clock in a <em>Keep exploring</em> run (split from {@link ExploreLoop}, 2026-11-21): the time the
 * run spent on it and what that time went to, its budget, and whether it is built, building, set aside or left as it
 * is. What is open on a screen and what the run still presses there are the loop's, asked through {@code open} and
 * {@code left}.
 */
final class ScreenClock {

    /** What a screen's clock went to, as a low-yield stop says it. */
    enum Spent {
        PRESSING("pressing"), SETTLING("settling"), WALKING("walking"), CLAUDE("waiting on Claude"), IDLE("idle");

        private final String label;

        Spent(String label) {
            this.label = label;
        }
    }

    /** The bounds of each screen's budget; null: no screen is ever set aside. */
    private final ScreenBudget bounds;
    private final LongSupplier nowMs;
    private final Consumer<ExploreEvent> emit;
    /** What is still to do on a screen before it is built, one line a kind; empty when nothing is. */
    private final Function<String, List<String>> open;
    /** What the run still presses itself on a screen, by name. */
    private final Function<String, List<String>> left;
    /** Told each screen said built, for a stop parked on its yield to be answered. */
    private final Consumer<String> onBuilt;

    /** Each screen's own clock, in ms: the time the run spent on it, charged at each round. */
    private final Map<String, Long> clock = new HashMap<>();
    /** The screens nothing is open about any more, said once each. */
    private final Set<String> built = new HashSet<>();
    /** What was open on each screen when the run last said it building. */
    private final Map<String, List<String>> leftOpen = new HashMap<>();
    /** Screens over their budget unbuilt: the run works another open screen first, and these once none is. */
    private final Set<String> aside = new HashSet<>();
    /** Each set-aside screen's clock when it was set aside: a second budget past it is a low-yield stop. */
    private final Map<String, Long> asideAt = new HashMap<>();
    /** The screens a low-yield stop was resumed with ignore on: left as they are for the run. */
    private final Set<String> leftAsIs = new HashSet<>();
    /** The low-yield stop resumed with a fresh clock on each screen, by screen: fixed once the screen is built. */
    private final Map<String, String> yieldResumed = new HashMap<>();
    /** What each screen's clock went to, in ms. */
    private final Map<String, EnumMap<Spent, Long>> spentOn = new HashMap<>();
    /** The presses this run made on each screen, ways and inputs: the work its budget counts beside what is left. */
    private final Map<String, Integer> pressedOn = new HashMap<>();
    /** The run's presses, and the ms their presses took: its measured press time. */
    private int pressCount;
    private long pressingMs;
    /** Each screen's largest budget so far, in ms. */
    private final Map<String, Long> budgets = new HashMap<>();
    /** The clock each open screen had when this run restarted, in ms: its budget counts from there. */
    private final Map<String, Long> restoredMs = new HashMap<>();
    /** What the last round did, which the time to the next one is charged to, but what it waited on a stop. */
    private Spent doing = Spent.PRESSING;
    /** What the run waited on at stops since the last round's charge, in ms. */
    private final EnumMap<Spent, Long> waited = new EnumMap<>(Spent.class);
    /** The screen the last round was on, null when on none; since when, epoch ms. */
    private String onScreen;
    private long onSince;

    ScreenClock(ScreenBudget bounds, LongSupplier nowMs, Consumer<ExploreEvent> emit,
                Function<String, List<String>> open, Function<String, List<String>> left, Consumer<String> onBuilt) {
        this.bounds = bounds;
        this.nowMs = nowMs;
        this.emit = emit;
        this.open = open;
        this.left = left;
        this.onBuilt = onBuilt;
    }

    /**
     * Charges the time since the last round to the screen it was on, and moves the run's clock to {@code screen}
     * (null: on no screen of the map). A screen the run leaves is said built, or building with what is still open on
     * it, a built one too, which a stop parked on it while there opens again; a screen it reaches with nothing open
     * is said built at once.
     */
    void moveTo(String screen) {
        long now = nowMs.getAsLong();
        if (onScreen != null) {
            long ms = Math.max(0, now - onSince);
            clock.merge(onScreen, ms, Long::sum);
            charge(onScreen, ms);
            if (!onScreen.equals(screen)) {
                List<String> openNow = open.apply(onScreen);
                if (openNow.isEmpty()) {
                    if (!built.contains(onScreen)) built(onScreen, now);
                } else if (built.remove(onScreen) | !openNow.equals(leftOpen.put(onScreen, openNow))) {
                    // Said when what is open changed: a hub passed through again and again says it once.
                    emit.accept(new ExploreEvent.Building(onScreen, clock.get(onScreen), openNow, now));
                }
            }
        }
        waited.clear();
        onScreen = screen;
        onSince = now;
        if (screen != null && !built.contains(screen) && open.apply(screen).isEmpty()) built(screen, now);
    }

    /** Splits {@code ms} charged to {@code screen}: what the run waited on at stops, the rest to what it did. */
    private void charge(String screen, long ms) {
        EnumMap<Spent, Long> on = spentOn.computeIfAbsent(screen, s -> new EnumMap<>(Spent.class));
        long rest = ms;
        for (Map.Entry<Spent, Long> wait : waited.entrySet()) {
            long part = Math.min(rest, wait.getValue());
            on.merge(wait.getKey(), part, Long::sum);
            rest -= part;
        }
        if (rest > 0) on.merge(doing, rest, Long::sum);
    }

    /** What the round now under way does: the time to the next round is charged to it. */
    void doing(Spent what) {
        doing = what;
    }

    /** A press made on {@code screen}: one more of its work, and of the run's measured presses. */
    void pressed(String screen) {
        pressedOn.merge(screen, 1, Integer::sum);
        pressCount++;
    }

    /** The press just made took {@code ms}. */
    void took(long ms) {
        pressingMs += ms;
    }

    /**
     * The run waited at a stop paused at {@code pausedAt}, on the plugin side's settling when {@code settling}, else
     * on Claude. A resume that gave the screen a fresh clock moved its start past the pause: only what follows it is
     * waited.
     */
    void waitedAt(long pausedAt, boolean settling) {
        waited.merge(settling ? Spent.SETTLING : Spent.CLAUDE,
                Math.max(0, nowMs.getAsLong() - Math.max(pausedAt, onSince)), Long::sum);
    }

    /**
     * {@code screen}'s budget: each press made on it this run and each input or way left on it, at the run's measured
     * press time, within the bounds' floor and cap, the floor before any press is measured; on top of the clock a
     * restarted run found it at. It never shrinks: a way refused, or a run of quick presses elsewhere, does not put a
     * screen over.
     */
    Duration budget(String screen) {
        long work = pressedOn.getOrDefault(screen, 0) + left.apply(screen).size();
        long perPress = pressCount == 0 ? 0 : pressingMs / pressCount;
        long ms = restoredMs.getOrDefault(screen, 0L) + bounds.fit(Duration.ofMillis(work * perPress)).toMillis();
        return Duration.ofMillis(budgets.merge(screen, ms, Math::max));
    }

    /**
     * Whether {@code screen} is set aside: unbuilt past its {@link #budget} on its own clock. Said once, when its clock
     * first runs over, with what is still open on it.
     */
    boolean overBudget(String screen) {
        if (bounds == null) return false;
        if (aside.contains(screen)) return true;
        long ms = clock.getOrDefault(screen, 0L);
        if (built.contains(screen) || ms < budget(screen).toMillis()) return false;
        List<String> openNow = open.apply(screen);
        if (openNow.isEmpty()) return false;
        aside.add(screen);
        asideAt.put(screen, ms);
        emit.accept(new ExploreEvent.SetAside(screen, ms, openNow, nowMs.getAsLong()));
        return true;
    }

    /** Whether {@code screen} is set aside. */
    boolean aside(String screen) {
        return aside.contains(screen);
    }

    /** Whether {@code screen} is said built. */
    boolean built(String screen) {
        return built.contains(screen);
    }

    /**
     * What the low-yield stop on {@code screen}, set aside and back on its own work, says: due once a second budget
     * ran on its clock since it was set aside with it still open; empty while not, or once it was ignored.
     */
    Optional<String> lowYield(String screen) {
        if (leftAsIs.contains(screen)) return Optional.empty();
        long ms = clock.getOrDefault(screen, 0L);
        // A screen a restarted run found aside gets its second budget from now.
        long since = asideAt.computeIfAbsent(screen, s -> ms);
        // The second budget is the screen's work alone: the clock a restart found is behind {@code since} already.
        if (ms - since < budget(screen).toMillis() - restoredMs.getOrDefault(screen, 0L)) return Optional.empty();
        List<String> openNow = open.apply(screen);
        if (openNow.isEmpty()) return Optional.empty();
        // A fresh clock that ran out too did not take.
        yieldResumed.remove(screen);
        List<String> leftNow = left.apply(screen);
        return Optional.of(screen + " is still unbuilt after " + JournalView.took(ms) + " on its clock, "
                + JournalView.took(ms - since) + " since it was set aside: " + String.join(", ", openNow)
                + (leftNow.isEmpty() ? "" : " (left: " + String.join(", ", leftNow) + ")")
                + "; its clock went to " + spent(screen)
                + ". Fix what is open and resume for a fresh clock, or resume with ignore to leave it as it is");
    }

    /** Where {@code screen}'s clock went, one part a kind ({@code "pressing 3m2s, walking 18s"}). */
    private String spent(String screen) {
        List<String> parts = new ArrayList<>();
        spentOn.getOrDefault(screen, new EnumMap<>(Spent.class)).forEach((kind, ms) -> {
            if (ms >= 1000) parts.add(kind.label + " " + JournalView.took(ms));
        });
        // Kept in memory only: a restarted run times from its restart.
        return parts.isEmpty() ? "nothing timed since the run started" : String.join(", ", parts);
    }

    /**
     * What a resume of the low-yield stop {@code stopId} on {@code screen} does: a fresh clock, the screen no longer
     * aside, or with {@code ignore} the screen left as it is for the run, never stopped on for its yield again. The
     * open stop resumed without ignore ({@code open}) is fixed once the screen is built on its fresh clock.
     */
    void yielded(String screen, String stopId, boolean ignore, boolean open) {
        if (built.contains(screen)) return;
        if (ignore) {
            leftAsIs.add(screen);
            return;
        }
        clock.put(screen, 0L);
        spentOn.remove(screen);
        aside.remove(screen);
        asideAt.remove(screen);
        // The wait on the stop itself is not charged to the fresh clock.
        if (screen.equals(onScreen)) {
            onSince = nowMs.getAsLong();
            waited.clear();
        }
        if (open) yieldResumed.put(screen, stopId);
    }

    private void built(String screen, long now) {
        built.add(screen);
        aside.remove(screen);
        asideAt.remove(screen);
        emit.accept(new ExploreEvent.Built(screen, clock.getOrDefault(screen, 0L), now));
        // A low-yield stop resumed with a fresh clock took once its screen is built; one parked on it is answered.
        String resumedId = yieldResumed.remove(screen);
        if (resumedId != null) emit.accept(new ExploreEvent.Fixed(resumedId, "built on its fresh clock", now));
        onBuilt.accept(screen);
    }

    /**
     * A restarted run's clocks go on where {@code journal} left them: its built screens stay built, and a built screen a
     * stop of the last run is still parked on is open again.
     */
    void restore(Journal journal) {
        JournalView.Build build = JournalView.of(journal).build();
        for (ExploreEvent.Built b : build.built()) {
            built.add(b.screen());
            clock.put(b.screen(), b.ms());
        }
        for (ExploreEvent.Building b : build.open()) {
            clock.put(b.screen(), b.ms());
            restoredMs.put(b.screen(), b.ms());
            leftOpen.put(b.screen(), b.open());
        }
        aside.addAll(build.aside());
        // A low-yield stop resumed with ignore leaves its screen as it is across the restart too.
        Map<String, String> lowYieldOn = new HashMap<>();
        for (ExploreEvent event : journal.events()) {
            if (event instanceof ExploreEvent.Stopped s && s.stop().kind() == StopKind.LOW_YIELD
                    && s.stop().screen() != null) lowYieldOn.put(s.stop().id(), s.stop().screen());
            if (event instanceof ExploreEvent.Fixed f && f.why().equals("ignored") && lowYieldOn.containsKey(f.stopId())) {
                leftAsIs.add(lowYieldOn.get(f.stopId()));
            }
        }
        long now = nowMs.getAsLong();
        for (String screen : List.copyOf(built)) {
            List<String> openNow = open.apply(screen);
            if (openNow.isEmpty()) continue;
            built.remove(screen);
            leftOpen.put(screen, openNow);
            emit.accept(new ExploreEvent.Building(screen, clock.getOrDefault(screen, 0L), openNow, now));
        }
    }
}
