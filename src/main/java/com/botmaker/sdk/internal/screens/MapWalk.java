package com.botmaker.sdk.internal.screens;

import com.botmaker.runlog.api.Level;
import com.botmaker.sdk.internal.flow.FlowWalker;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.LongSupplier;

/**
 * A walk over a whole screen map, independent of the bot's {@code main}: every screen reached breadth first from
 * the start, every edge taken once, and what did not go as the map says written down as findings.
 *
 * <p>Pure: the game is behind {@link Moves} and the clock is {@code nowMs}, so tests script both. Never stuck
 * forever: an edge that fails is noted and the walk goes on; a screen it cannot get back to is recovered, then the
 * game restarted, and the walk gives up after {@link Budgets#restarts} restarts or past {@link Budgets#whole}. Only a
 * stop ({@code Bot.stop()}) leaves it by an exception.
 */
public final class MapWalk {

    private MapWalk() {}

    /** Something the walk met that the map does not say: {@code why} in words, {@code from}/{@code to} the screens. */
    public record Finding(Kind kind, String from, String to, String why) {

        public enum Kind {
            /** An edge that did not arrive where the map says: elsewhere, nowhere, or not in time. */
            EDGE_MISSED("edge_missed", "Edge missed", Level.WARN),
            /** A screen its edge led to that showed no screen of the map: its pictures no longer match. */
            PICTURES_CHANGED("pictures_changed", "Pictures changed", Level.ERROR),
            /** A screen the walk never reached. */
            UNREACHED("unreached", "Never reached", Level.ERROR);

            private final String id;
            private final String displayName;
            private final Level level;

            Kind(String id, String displayName, Level level) {
                this.id = id;
                this.displayName = displayName;
                this.level = level;
            }

            public String id() {
                return id;
            }

            public String displayName() {
                return displayName;
            }

            /** The level of the line that reports it. */
            public Level level() {
                return level;
            }
        }
    }

    /** Where the game is after an edge was taken. */
    public enum Arrival { AT_TARGET, ELSEWHERE, UNKNOWN, UNCHANGED, TIMED_OUT }

    /** An edge's outcome: how it arrived, and {@code at}, the screen the game shows, null when none of the map. */
    public record Took(Arrival arrival, String at) {}

    /**
     * The game, as the walk moves it. Each call may throw {@code Bot.stop()}'s exception, which ends the walk; any
     * other exception counts as that move failing.
     */
    public interface Moves {

        /** Walks to {@code screen} by the map; whether the game shows it now. */
        boolean goTo(String screen);

        /** Presses {@code edge}'s input from its {@code from} (or waits, for one {@link ScreenGraph.Edge#onItsOwn}). */
        Took take(ScreenGraph.Edge edge);

        /** Walks to any screen of the map; whether it got there. */
        boolean recover();

        /** Restarts the game and walks to its home. */
        void restart();
    }

    /** Time for one edge, time for the whole walk, and how many game restarts it may spend. */
    public record Budgets(Duration edge, Duration whole, int restarts) {

        public static final Budgets DEFAULT = new Budgets(Duration.ofSeconds(30), Duration.ofMinutes(30), 5);
    }

    /**
     * What the walk did: the map's {@code screens} and how many it {@code reached}; its {@code edges}, how many
     * arrived ({@code ok}) and how many were never tried (deadline, restart cap, a screen it could not get back to).
     */
    public record Summary(int screens, int reached, int edges, int ok, int notTried, List<Finding> findings) {}

    /** Walks {@code graph} through {@code moves} within {@code budgets}, timed by {@code nowMs}. */
    public static Summary walk(ScreenGraph graph, Moves moves, Budgets budgets, LongSupplier nowMs) {
        List<ScreenGraph.Edge> edges = graph.edges();
        int screens = graph.screens().size();
        Optional<String> start = graph.start();
        if (start.isEmpty()) {
            return new Summary(screens, 0, edges.size(), 0, edges.size(),
                    List.of(new Finding(Finding.Kind.UNREACHED, null, null, "the map has no start screen")));
        }
        Map<String, List<ScreenGraph.Edge>> out = new LinkedHashMap<>();
        Map<String, List<ScreenGraph.Edge>> in = new LinkedHashMap<>();
        for (ScreenGraph.Edge edge : edges) {
            out.computeIfAbsent(edge.from(), k -> new ArrayList<>()).add(edge);
            in.computeIfAbsent(edge.to(), k -> new ArrayList<>()).add(edge);
        }

        long began = nowMs.getAsLong();
        long whole = budgets.whole().toMillis();
        long perEdge = budgets.edge().toMillis();
        Set<String> reached = new LinkedHashSet<>(List.of(start.get()));
        Deque<String> queue = new ArrayDeque<>(reached);
        Set<ScreenGraph.Edge> tried = new LinkedHashSet<>();
        Map<String, String> unknown = new LinkedHashMap<>();
        List<Finding> findings = new ArrayList<>();
        int ok = 0;
        int restarts = 0;

        walk:
        while (!queue.isEmpty()) {
            String screen = queue.poll();
            for (ScreenGraph.Edge edge : out.getOrDefault(screen, List.of())) {
                if (nowMs.getAsLong() - began > whole) break walk;
                // An edge that leads elsewhere now, by its own method, is not taken: no finding of a healthy map.
                if (!leadsNow(edge)) continue;
                if (!goTo(moves, screen) && !(recover(moves) && goTo(moves, screen))) {
                    boolean back = false;
                    if (restarts < budgets.restarts()) {
                        restarts++;
                        restart(moves);
                        back = goTo(moves, screen);
                    }
                    if (!back) {
                        // The last restart allowed is spent and the game still lost: the walk ends on this screen.
                        if (restarts >= budgets.restarts()) {
                            findings.add(new Finding(Finding.Kind.UNREACHED, screen, null,
                                    "gave up after " + restarts + " restarts"));
                            break walk;
                        }
                        continue walk;
                    }
                }
                tried.add(edge);
                long before = nowMs.getAsLong();
                Took took;
                String crash = null;
                try {
                    took = moves.take(edge);
                } catch (RuntimeException e) {
                    rethrowStop(e);
                    took = null;
                    crash = e.toString();
                }
                Arrival arrival = took == null || took.arrival() == null || nowMs.getAsLong() - before > perEdge
                        ? Arrival.TIMED_OUT : took.arrival();
                switch (arrival) {
                    case AT_TARGET -> {
                        ok++;
                        // A popup is left by its own rules, never walked to: its edges are not taken.
                        boolean popup = graph.screen(edge.to()).map(MapScreen::isPopup).orElse(false);
                        if (reached.add(edge.to()) && !popup) queue.add(edge.to());
                    }
                    case ELSEWHERE -> findings.add(missed(edge, took.at() == null ? "arrived elsewhere"
                            : "arrived at " + took.at()));
                    case UNCHANGED -> findings.add(missed(edge, "nothing happened"));
                    case TIMED_OUT -> findings.add(missed(edge, crash != null ? "failed: " + crash
                            : "no screen after " + budgets.edge().toSeconds() + " s"));
                    case UNKNOWN -> unknown.putIfAbsent(edge.to(), edge.from());
                }
            }
        }

        unknown.forEach((to, from) -> {
            if (!reached.contains(to)) findings.add(new Finding(Finding.Kind.PICTURES_CHANGED, from, to,
                    "its edge from " + from + " showed no screen of the map"));
        });
        for (Map.Entry<String, MapScreen> entry : graph.screens().entrySet()) {
            String screen = entry.getKey();
            if (reached.contains(screen) || unknown.containsKey(screen) || entry.getValue().isPopup()) continue;
            List<ScreenGraph.Edge> into = in.getOrDefault(screen, List.of());
            if (into.isEmpty()) {
                findings.add(new Finding(Finding.Kind.UNREACHED, null, screen, "no edge leads to it"));
                continue;
            }
            // Behind the first screen, in the order the walk reached them, whose edge to it was tried; a screen
            // behind edges never tried is no finding.
            reached.stream().filter(from -> into.stream().anyMatch(e -> e.from().equals(from) && tried.contains(e)))
                    .findFirst()
                    .ifPresent(from -> findings.add(new Finding(Finding.Kind.UNREACHED, from, screen,
                            "behind " + from)));
        }
        return new Summary(screens, reached.size(), edges.size(), ok, edges.size() - tried.size(),
                List.copyOf(findings));
    }

    private static Finding missed(ScreenGraph.Edge edge, String why) {
        return new Finding(Finding.Kind.EDGE_MISSED, edge.from(), edge.to(), why);
    }

    // Each move that throws anything but a stop failed: it did not get there, or its edge timed out.

    private static boolean goTo(Moves moves, String screen) {
        try {
            return moves.goTo(screen);
        } catch (RuntimeException e) {
            rethrowStop(e);
            return false;
        }
    }

    private static boolean recover(Moves moves) {
        try {
            return moves.recover();
        } catch (RuntimeException e) {
            rethrowStop(e);
            return false;
        }
    }

    private static void restart(Moves moves) {
        try {
            moves.restart();
        } catch (RuntimeException e) {
            rethrowStop(e);
        }
    }

    /** Whether {@code edge}'s own method ({@code Way.when}) says it leads there now; true without one. */
    private static boolean leadsNow(ScreenGraph.Edge edge) {
        if (edge.when() == null) return true;
        try {
            return edge.when().getAsBoolean();
        } catch (RuntimeException e) {
            rethrowStop(e);
            return false;
        }
    }

    /** Rethrows {@code e} when it is a stop, or wraps one (a move run on an executor). */
    private static void rethrowStop(RuntimeException e) {
        for (Throwable at = e; at != null; at = at.getCause() == at ? null : at.getCause()) {
            if (FlowWalker.isStop(at)) throw (RuntimeException) at;
        }
    }
}
