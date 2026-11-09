package com.botmaker.sdk.internal.explore;

import com.botmaker.sdk.api.screens.Setup;
import com.botmaker.sdk.internal.screens.MapScreen;
import com.botmaker.sdk.internal.screens.MapWalk.Arrival;
import com.botmaker.sdk.internal.screens.MapWalk.Took;
import com.botmaker.sdk.internal.screens.ScreenGraph;
import com.botmaker.sdk.internal.screens.ScreenGraph.Edge;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static com.botmaker.sdk.internal.screens.MapFixtures.click;
import static com.botmaker.sdk.internal.screens.MapFixtures.ref;
import static com.botmaker.sdk.internal.screens.MapFixtures.screen;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Keep exploring's loop over a scripted game: what stops it, what parks, what a resume checks. */
class ExploreLoopTest {

    @TempDir
    Path folder;

    /** A, the start, opens B and D, each free; B and D have their way back. */
    private static ScreenGraph map() {
        Map<String, MapScreen> screens = new LinkedHashMap<>();
        screens.put("A", screen(Setup.named("A").start(), click("b").free(), click("d").free()));
        screens.put("B", screen(Setup.named("B").from(ref("A", "b"))));
        screens.put("D", screen(Setup.named("D").from(ref("A", "d"))));
        return ScreenGraph.of(screens);
    }

    /** A game that does what {@code arrival} says, at its target by default; its clock moves only while idle. */
    private static final class Game implements ExploreLoop.Play {
        String at = "A";
        long nowMs;
        Foreground front = Foreground.UNKNOWN;
        Function<Edge, Took> arrival = edge -> new Took(Arrival.AT_TARGET, edge.to());
        Runnable onIdle = () -> {};
        final List<String> taken = new ArrayList<>();

        @Override
        public ExploreLoop.Look look(String screen) {
            return ExploreLoop.Look.NOTHING;
        }

        @Override
        public Guards.Around around(Edge edge) {
            return new Guards.Around(List.of(), front, List.of());
        }

        @Override
        public Foreground front() {
            return front;
        }

        @Override
        public String keep(String name) {
            return "";
        }

        @Override
        public boolean planRound() {
            return false;
        }

        @Override
        public void idle(Duration time) {
            nowMs += time.toMillis();
            onIdle.run();
        }

        @Override
        public Optional<String> where() {
            return Optional.ofNullable(at);
        }

        @Override
        public boolean goTo(String screen) {
            at = screen;
            return true;
        }

        @Override
        public Took take(Edge edge, long withinMs) {
            taken.add(ExploreLoop.key(edge));
            Took took = arrival.apply(edge);
            at = took.at();
            return took;
        }

        @Override
        public boolean recover() {
            at = "A";
            return true;
        }

        @Override
        public void restart() {
            at = "A";
        }
    }

    private Journal journal() {
        return new Journal(folder);
    }

    private ExploreLoop loop(ScreenGraph map, Game game, ExploreLoop.Reload reload, Duration within) {
        return new ExploreLoop(map, game, reload, Guards.with(List.of()), journal(),
                ExploreLoop.Settings.DEFAULT.within(within), () -> game.nowMs);
    }

    private static ExploreLoop.Reload same(ScreenGraph map) {
        return () -> new ExploreLoop.Reload.Reloaded(1, map, null, List.of());
    }

    private List<ExploreEvent.Stopped> stops() {
        return journal().events().stream().filter(ExploreEvent.Stopped.class::isInstance)
                .map(ExploreEvent.Stopped.class::cast).toList();
    }

    private <T extends ExploreEvent> List<T> events(Class<T> type) {
        return journal().events().stream().filter(type::isInstance).map(type::cast).toList();
    }

    /** The open stop's id, the last one stopped on. */
    private String openStop() {
        return stops().getLast().stop().id();
    }

    @Test
    void unknownScreenStops() {
        Game game = new Game();
        game.arrival = edge -> edge.to().equals("B") ? new Took(Arrival.UNKNOWN, null) : new Took(Arrival.AT_TARGET, edge.to());

        loop(map(), game, same(map()), Duration.ofMinutes(12)).run();

        ExploreEvent.Stopped stopped = stops().getFirst();
        assertEquals(StopKind.UNKNOWN_SCREEN, stopped.stop().kind());
        assertEquals("A.b→B", stopped.stop().place(), "where it came from");
        assertTrue(stopped.stop().lastPresses().contains("A: b"), stopped.stop().lastPresses().toString());
        assertEquals(1, events(ExploreEvent.Parked.class).size(), "nobody answered in 10 min");
    }

    @Test
    void newScreenIsAnEventNotAStop() {
        Game game = new Game();

        assertEquals(ExploreLoop.End.BUDGET, loop(map(), game, same(map()), Duration.ofMinutes(5)).run());

        List<ExploreEvent.NewScreen> found = events(ExploreEvent.NewScreen.class);
        assertEquals(List.of("A", "B", "D"), found.stream().map(ExploreEvent.NewScreen::screen).toList());
        assertEquals("A", found.get(1).from());
        assertEquals("b", found.get(1).by());
        assertTrue(stops().isEmpty(), stops().toString());
        assertTrue(game.taken.containsAll(List.of("A.b→B", "A.d→D")), game.taken.toString());
    }

    @Test
    void untaggedMapGivesOneBatchStop() {
        Map<String, MapScreen> screens = new LinkedHashMap<>();
        screens.put("A", screen(Setup.named("A").start(), click("b"), click("d")));
        screens.put("B", screen(Setup.named("B").from(ref("A", "b"))));
        screens.put("D", screen(Setup.named("D").from(ref("A", "d"))));
        Game game = new Game();

        loop(ScreenGraph.of(screens), game, same(map()), Duration.ofMinutes(30)).run();

        assertEquals(1, stops().size());
        Stop batch = stops().getFirst().stop();
        assertEquals(StopKind.NO_VERDICT, batch.kind());
        assertTrue(batch.missing().contains("A.b") && batch.missing().contains("A.d"), batch.missing());
        assertTrue(game.taken.isEmpty(), "nothing pressed before every input has a verdict");
        assertTrue(events(ExploreEvent.Parked.class).isEmpty(), "never parked");
    }

    @Test
    void parkedStopIsAvoidedAndNotRepeated() {
        Game game = new Game();
        game.arrival = edge -> edge.to().equals("B") ? new Took(Arrival.ELSEWHERE, "D") : new Took(Arrival.AT_TARGET, edge.to());

        loop(map(), game, same(map()), Duration.ofMinutes(14)).run();

        List<ExploreEvent.Stopped> wrong = stops().stream()
                .filter(s -> s.stop().kind() == StopKind.WRONG_ARRIVAL).toList();
        assertEquals(1, wrong.size(), stops().toString());
        assertEquals(1, game.taken.stream().filter("A.b→B"::equals).count(), "its place avoided once parked");
        assertEquals(List.of(wrong.getFirst().stop().id()), journal().queue().stream().map(Stop::id).toList());
        assertTrue(game.taken.contains("A.d→D"), "the run went on elsewhere");
    }

    @Test
    void foreignWindowNeverParks() {
        Game game = new Game();
        game.front = new Foreground("com.android.chrome", "com.game");
        game.onIdle = () -> {
            if (game.nowMs >= Duration.ofMinutes(30).toMillis()) game.front = new Foreground("com.game", "com.game");
        };

        loop(map(), game, same(map()), Duration.ofMinutes(35)).run();

        Stop guard = stops().getFirst().stop();
        assertEquals(StopKind.GUARD, guard.kind());
        assertTrue(guard.missing().contains("com.android.chrome"), guard.missing());
        assertTrue(events(ExploreEvent.Parked.class).isEmpty(), "never parked, 30 min in front");
        assertEquals(guard.id(), events(ExploreEvent.Fixed.class).getFirst().stopId());
        assertTrue(game.taken.contains("A.b→B"), "pressed once the game was back");
    }

    @Test
    void resumeCommandReloadsAndChecksTheStopIsGone() {
        Game game = new Game();
        game.arrival = edge -> edge.to().equals("B") ? new Took(Arrival.ELSEWHERE, "D") : new Took(Arrival.AT_TARGET, edge.to());
        int[] reloads = {0};
        ExploreLoop.Reload reload = () -> {
            reloads[0]++;
            if (reloads[0] == 1) return new ExploreLoop.Reload.Reloaded(0, map(), null, List.of("Shop.java:3: ';' expected"));
            // The second compiles but fixes nothing; the third fixes the way.
            if (reloads[0] == 3) game.arrival = edge -> new Took(Arrival.AT_TARGET, edge.to());
            return new ExploreLoop.Reload.Reloaded(reloads[0] - 1, map(), null, List.of());
        };
        game.onIdle = () -> {
            if (!stops().isEmpty() && game.nowMs % 60_000 == 0) journal().command("resume " + openStop());
        };

        loop(map(), game, reload, Duration.ofMinutes(8)).run();

        List<ExploreEvent.Reloaded> reloaded = events(ExploreEvent.Reloaded.class);
        assertTrue(reloaded.getFirst().refused(), "a compile error refuses the resume");
        List<ExploreEvent.Stopped> stopped = stops();
        assertEquals(2, stopped.size(), stopped.toString());
        assertFalse(stopped.getFirst().again());
        assertTrue(stopped.get(1).again(), "the same stop at once after a reload");
        assertTrue(stopped.get(1).stop().missing().startsWith("the fix did not take"), stopped.get(1).stop().missing());
        List<ExploreEvent.Fixed> fixed = events(ExploreEvent.Fixed.class);
        assertEquals(List.of(stopped.get(1).stop().id()), fixed.stream().map(ExploreEvent.Fixed::stopId).toList(),
                "only the resume that took is fixed");
        assertEquals(3, game.taken.stream().filter("A.b→B"::equals).count());
    }

    @Test
    void staleResumeDoesNotHoldParking() {
        Game game = new Game();
        game.arrival = edge -> edge.to().equals("B") ? new Took(Arrival.ELSEWHERE, "D") : new Took(Arrival.AT_TARGET, edge.to());
        game.onIdle = () -> {
            if (game.nowMs % 60_000 == 0) journal().command("resume crash-99");
        };

        loop(map(), game, same(map()), Duration.ofMinutes(12)).run();

        assertEquals(1, events(ExploreEvent.Parked.class).size(), "parked on time despite the stale resumes");
        assertTrue(events(ExploreEvent.Reloaded.class).isEmpty(), "a stale resume reloads nothing");
    }

    @Test
    void newerJournalReadsAsUnknown() throws IOException {
        Files.createDirectories(folder);
        Files.writeString(folder.resolve(Journal.QUEUE), "[{\"id\":\"later-1\",\"kind\":\"LATER_KIND\",\"place\":\"A\","
                + "\"texts\":[],\"elements\":[],\"facts\":{},\"lastPresses\":[],\"added\":1}]");
        Files.writeString(folder.resolve(Journal.EVENTS), "{\"type\":\"tried\",\"screen\":\"A\",\"by\":\"b\","
                + "\"mode\":\"LATER_MODE\",\"arrival\":\"LATER_ARRIVAL\",\"at\":1}\n");

        assertEquals(StopKind.UNKNOWN, journal().queue().getFirst().kind());
        ExploreEvent.Tried tried = (ExploreEvent.Tried) journal().events().getFirst();
        assertEquals(ExploreLoop.Mode.UNKNOWN, tried.mode());
        assertEquals(null, tried.arrival());
    }

    @Test
    void journalReadsBackAfterACutLine() throws IOException {
        Journal journal = journal();
        journal.append(new ExploreEvent.NewScreen("A", null, null, 1));
        Stop stop = new Stop("guard-1", StopKind.GUARD, "A", "A", "", List.of(), List.of(), Map.of(), List.of(),
                "x", 2);
        journal.append(new ExploreEvent.Stopped(stop, false, 2));
        journal.queue(List.of(stop));
        // A JVM killed mid-write: half a line, cut inside a character.
        byte[] cut = "{\"type\":\"tried\",\"screen\":\"é".getBytes(StandardCharsets.UTF_8);
        Files.write(folder.resolve(Journal.EVENTS), Arrays.copyOf(cut, cut.length - 1), StandardOpenOption.APPEND);

        List<ExploreEvent> events = new Journal(folder).events();

        assertEquals(2, events.size(), events.toString());
        assertEquals(stop, ((ExploreEvent.Stopped) events.get(1)).stop());
        assertEquals(List.of(stop), new Journal(folder).queue());
        journal.command("resume guard-1 ignore");
        assertEquals(Optional.of(new Journal.Command.Resume("guard-1", true)), journal.takeCommand());
        assertEquals(Optional.empty(), journal.takeCommand(), "read once");
    }
}
