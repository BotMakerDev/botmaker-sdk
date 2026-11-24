// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.internal.explore;

import com.botmaker.sdk.api.screens.Answer;
import com.botmaker.sdk.api.screens.Button;
import com.botmaker.sdk.api.screens.Verdict;
import com.botmaker.sdk.internal.screens.Conditions;
import com.botmaker.sdk.internal.screens.MapScreen;
import com.botmaker.sdk.internal.screens.ScreenGraph;
import com.botmaker.sdk.internal.screens.ScreenGraph.Edge;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

/**
 * What a <em>Keep exploring</em> run presses next, over the map it plays (split from {@link ExploreLoop},
 * 2026-11-21): the ways and inputs it tried this run, the ways not on their screen when it looked, and from those the
 * way to press here, the inputs left here and the screen to walk to.
 */
final class Chooser {

    private final ExploreLoop.Play play;
    /** Whether the game reads a tree, so the run presses a screen's inputs itself. */
    private final BooleanSupplier readsTrees;
    /** Whether a stop is parked on a place: it is not pressed until one is fixed. */
    private final Predicate<String> avoided;
    private ScreenGraph map;

    private final Set<String> tried = new HashSet<>();
    /**
     * The ways whose button was not on their screen when the run looked, this run: absent is not tried, so each is
     * looked for again once the run reaches its screen again, and is tried only when absent that second time too.
     * Never restored: a restarted run looks again.
     */
    private final Set<String> notSeen = new HashSet<>();
    /** The ways of {@link #notSeen} looked for on this visit of the screen the run is on: not looked for again on it. */
    private final Set<String> notSeenHere = new HashSet<>();
    /** The last screen of the map a round was on, a frame no screen matched skipped; null before the first. */
    private String lastScreen;

    Chooser(ScreenGraph map, ExploreLoop.Play play, BooleanSupplier readsTrees, Predicate<String> avoided) {
        this.map = map;
        this.play = play;
        this.readsTrees = readsTrees;
        this.avoided = avoided;
    }

    /** Chooses over {@code map} from now on: the bot was loaded again. */
    void use(ScreenGraph map) {
        this.map = map;
    }

    /** The ways and inputs tried this run, by key, as a run's memory keeps them. */
    Set<String> tried() {
        return Collections.unmodifiableSet(tried);
    }

    /** Takes back {@code keys}, tried by the run a restart goes on from. */
    void triedBefore(Collection<String> keys) {
        tried.addAll(keys);
    }

    /** Whether {@code key}, a way or an input, was tried this run. */
    boolean tried(String key) {
        return tried.contains(key);
    }

    /** Counts {@code key}, a way or an input, as tried: it is not pressed again this run. */
    void tryOnce(String key) {
        tried.add(key);
    }

    /**
     * The run is on {@code screen} (null: on none of the map). A screen left for another and reached again is looked
     * at afresh: its ways not seen are looked for again. A frame no screen matched between two of the same one is no
     * visit elsewhere.
     */
    void visit(String screen) {
        if (screen != null && !screen.equals(lastScreen)) notSeenHere.clear();
        if (screen != null) lastScreen = screen;
    }

    /**
     * No screen to leave for, so none to come back from: the ways not seen here are tried, not waited on. False when
     * there were none.
     */
    boolean giveUpNotSeen() {
        if (notSeenHere.isEmpty()) return false;
        tried.addAll(notSeenHere);
        notSeenHere.clear();
        return true;
    }

    /**
     * Frees {@code place} to be pressed again: the place itself and, for a crash, the way or input it names, so a fix
     * shows only once that press is made again.
     */
    void retry(StopKind kind, String place) {
        tried.remove(place);
        notSeen.remove(place);
        // A fault's place is the crash's when a crash raised it.
        String press = kind == StopKind.CRASH || kind == StopKind.FAULT ? ExploreLoop.pressOf(place) : null;
        if (press != null) {
            tried.removeIf(t -> t.equals(press) || t.startsWith(press + "→"));
            notSeen.removeIf(t -> t.startsWith(press + "→"));
        }
    }

    /**
     * The first edge out of {@code screen} not tried, not avoided, open now. One the moves cannot press here (a custom
     * input, a way back with no close button seen) counts as tried, so no walk comes back for it.
     */
    Optional<Edge> untried(String screen) {
        for (Edge edge : map.edges()) {
            // One taken by waiting is not pressed: the game takes it by itself (untriedEdge leaves it out).
            if (!edge.from().equals(screen) || !untriedEdge(edge)) continue;
            String key = ExploreLoop.key(edge);
            if (notSeenHere.contains(key)) continue;
            if (open(edge)) return Optional.of(edge);
            // Not there now: looked for again on the next visit, tried once absent then too.
            if (notSeen.add(key)) notSeenHere.add(key);
            else tried.add(key);
        }
        return Optional.empty();
    }

    /** Whether {@code edge} is a way the run still presses: not on its own, not tried, not avoided, its when holding. */
    boolean untriedEdge(Edge edge) {
        String key = ExploreLoop.key(edge);
        // An unknown never closes a way: one behind a fact not read yet is pressed, as the screen map asks it.
        return !edge.onItsOwn() && !tried.contains(key) && !avoided.test(key) && Conditions.holds(edge.when());
    }

    /** How many ways out of {@code screen} wait for their condition to hold: it says no now. */
    long waiting(String screen) {
        return map.edges().stream().filter(e -> e.from().equals(screen) && !e.onItsOwn()
                && !tried.contains(ExploreLoop.key(e)) && !avoided.test(ExploreLoop.key(e))
                && Conditions.answer(e.when()) == Answer.NO).count();
    }

    private boolean open(Edge edge) {
        try {
            return play.open(edge);
        } catch (RuntimeException e) {
            ExploreLoop.rethrowStop(e);
            return true;
        }
    }

    /**
     * The inputs of {@code screen} the run presses itself, in the map's order ({@link ScreenGraph#left}: untried or
     * failed, information last): no way, free, found in its tree, not pressed yet this run. One the bot's code presses
     * ({@code Custom}) or one found by a picture is Claude's. A failed one is pressed again once a run: what failed may
     * have been the page's, or an older run's judging.
     */
    List<String> inputsLeft(String screen) {
        MapScreen on = map.screen(screen).orElse(null);
        if (on == null) return List.of();
        Set<String> ways = new HashSet<>();
        for (Edge edge : map.edges()) if (edge.from().equals(screen) && edge.input() != null) ways.add(edge.input());
        List<String> left = new ArrayList<>();
        for (String field : map.left(screen)) {
            if (!(on.inputs().get(field) instanceof Button button) || button.element() == null) continue;
            if (!(button.verdict() instanceof Verdict.Free)) continue;
            String key = screen + "." + field;
            if (ways.contains(field) || tried.contains(key) || avoided.test(key)) continue;
            left.add(field);
        }
        return left;
    }

    /**
     * Why the run presses no input on {@code screen}: each input the map still counts as left, with what rules it out,
     * for the line a run that idles there says once.
     */
    String whyNoInput(String screen) {
        MapScreen on = map.screen(screen).orElse(null);
        if (on == null) return "the map holds no such screen";
        List<String> fields = map.left(screen);
        if (fields.isEmpty()) {
            List<String> all = new ArrayList<>();
            for (String field : map.fields(screen)) all.add(field + " " + map.status(screen, field));
            return "the map counts no input left on it; its fields: " + (all.isEmpty() ? "none" : all);
        }
        Set<String> ways = new HashSet<>();
        for (Edge edge : map.edges()) if (edge.from().equals(screen) && edge.input() != null) ways.add(edge.input());
        List<String> why = new ArrayList<>();
        for (String field : fields) {
            String key = screen + "." + field;
            String reason = !(on.inputs().get(field) instanceof Button button) ? "not a button"
                    : button.element() == null ? "a button with no element"
                    : !(button.verdict() instanceof Verdict.Free) ? "verdict " + button.verdict()
                    : ways.contains(field) ? "already a way"
                    : tried.contains(key) ? "tried this run"
                    : avoided.test(key) ? "avoided" : "pressable";
            why.add(field + ": " + reason);
        }
        return String.join("; ", why);
    }

    /** What the run still presses itself on {@code screen}: its ways not tried, then its inputs left, by name. */
    List<String> left(String screen) {
        List<String> left = new ArrayList<>();
        map.edges().stream().filter(e -> e.from().equals(screen) && untriedEdge(e)).forEach(e -> left.add(e.label()));
        if (readsTrees.getAsBoolean()) left.addAll(inputsLeft(screen));
        return left;
    }

    /**
     * The screen the run walks to from {@code screen}: the map's next to explore ({@link ScreenGraph#nextToExplore}),
     * nearest first, among the screens {@code also} lets through with work the run does itself there, an input
     * {@link ScreenGraph#left} or a way not taken this run (a done screen's ways are still taken once a run, which
     * proves them); not {@code screen}.
     */
    Optional<String> next(String screen, Predicate<String> also) {
        // One no way reaches from here comes last in the map's order: then none is reached, and the run waits here.
        return map.nextToExplore(Optional.of(screen), to -> !to.equals(screen) && also.test(to) && hasWork(to))
                // Not through a way not on the screen now.
                .filter(to -> map.pathTo(screen, to, edge -> !notSeenHere.contains(ExploreLoop.key(edge))).isPresent());
    }

    /** Whether the run has work of its own on {@code screen}: a way not taken this run, or an input it presses. */
    private boolean hasWork(String screen) {
        // A website's input the run presses itself is work too: a dialog's button is no edge until pressed.
        return map.edges().stream().anyMatch(e -> e.from().equals(screen) && untriedEdge(e))
                || readsTrees.getAsBoolean() && !inputsLeft(screen).isEmpty();
    }
}
