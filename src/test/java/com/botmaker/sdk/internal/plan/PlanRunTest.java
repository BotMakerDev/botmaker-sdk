package com.botmaker.sdk.internal.plan;

import com.botmaker.sdk.api.plan.Goal;
import com.botmaker.sdk.api.plan.Need;
import com.botmaker.sdk.api.plan.Plan;
import com.botmaker.sdk.api.screens.Answer;
import com.botmaker.sdk.api.screens.Condition;
import com.botmaker.sdk.api.screens.Do;
import com.botmaker.sdk.api.screens.Input;
import com.botmaker.sdk.api.screens.Pressed;
import com.botmaker.sdk.api.screens.Retry;
import com.botmaker.sdk.api.screens.Setup;
import com.botmaker.sdk.internal.params.fixture.params.bot.Hero;
import com.botmaker.sdk.internal.plan.PlanRun.End;
import com.botmaker.sdk.internal.plan.Round.Step;
import com.botmaker.sdk.internal.screens.MapScreen;
import com.botmaker.sdk.internal.screens.ScreenGraph;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static com.botmaker.sdk.internal.screens.MapFixtures.click;
import static com.botmaker.sdk.internal.screens.MapFixtures.ref;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A plan run against a fake game: what it walks to, presses, reads and waits for, and when it ends. */
class PlanRunTest {

    private static final Instant NOON = Instant.parse("2026-11-06T12:00:00Z");

    @TempDir
    Path dir;

    private final Map<String, Map<String, Do>> placed = new LinkedHashMap<>();
    private final MovingClock clock = new MovingClock(NOON);

    /** A clock the fake game moves on as it waits. */
    private static final class MovingClock extends Clock {
        private Instant now;

        MovingClock(Instant now) {
            this.now = now;
        }

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

    /**
     * The game as the run sees it: where it is, what each press comes to and what it changes, which facts each screen
     * feeds and which are fresh. It moves the clock a second at each walk and press, and by the time it idles.
     */
    private class FakeGame implements PlanRun.Game {
        String at = "HOME";
        final List<String> did = new ArrayList<>();
        final List<String> said = new ArrayList<>();
        final Map<String, Pressed.Result> results = new HashMap<>();
        final Map<String, Runnable> effects = new HashMap<>();
        final Map<Method, List<String>> feeds = new HashMap<>();
        final List<Duration> idled = new ArrayList<>();

        @Override
        public Optional<String> where() {
            return Optional.of(at);
        }

        @Override
        public boolean walk(String screen) {
            did.add("walk " + screen);
            at = screen;
            tick();
            return true;
        }

        @Override
        public Pressed.Result press(Step step) {
            did.add("press " + step.key());
            tick();
            Pressed.Result result = results.getOrDefault(step.key(), Pressed.Result.DONE);
            if (result == Pressed.Result.DONE || result == Pressed.Result.LEFT) {
                effects.getOrDefault(step.key(), () -> { }).run();
            }
            return result;
        }

        @Override
        public void readAgain(String screen, Set<Method> facts) {
            did.add("read " + screen);
        }

        @Override
        public List<String> screensOf(Method fact) {
            return feeds.getOrDefault(fact, List.of());
        }

        @Override
        public boolean fresh(Method fact) {
            return false;
        }

        @Override
        public void idle(Duration time) {
            idled.add(time);
            clock.now = clock.now.plus(time);
        }

        @Override
        public void say(String line, Map<String, String> values) {
            said.add(line);
        }

        private void tick() {
            clock.now = clock.now.plusSeconds(1);
        }
    }

    private Do on(String screen, String field, Do action) {
        placed.computeIfAbsent(screen, s -> new LinkedHashMap<>()).put(field, action);
        return action;
    }

    /** A goal met once {@code flag} is set: its own, never equal to another. */
    private static Goal metWhen(AtomicBoolean flag) {
        return Goal.of(new Condition() {
            @Override
            public Answer answer() {
                return Answer.of(flag.get());
            }
        });
    }

    /** HOME opens SHOP and ARENA, each with its way back; each holds what {@link #on} put there. */
    private ScreenGraph map() {
        Map<String, MapScreen> screens = new LinkedHashMap<>();
        screens.put("HOME", screen(Setup.named("HOME").start(), "HOME", Map.of("shop", click("shop"), "arena", click("arena"))));
        screens.put("SHOP", screen(Setup.named("SHOP").from(ref("HOME", "shop")), "SHOP", Map.of()));
        screens.put("ARENA", screen(Setup.named("ARENA").from(ref("HOME", "arena")), "ARENA", Map.of()));
        return ScreenGraph.of(screens);
    }

    private MapScreen screen(Setup setup, String constant, Map<String, Input> ways) {
        Map<String, Input> inputs = new LinkedHashMap<>(ways);
        Map<String, Do> actions = placed.getOrDefault(constant, Map.of());
        actions.forEach((field, action) -> inputs.put(field, action.input()));
        return new MapScreen(setup, inputs, Map.of(), Map.of(), actions);
    }

    private DoneRecords records() {
        return new DoneRecords(dir.resolve("done.json"), clock);
    }

    private PlanRun run(Plan plan, FakeGame game, DoneRecords records) {
        Function<Goal, String> names = goal -> "GOAL";
        return new PlanRun(plan, map(), records, game, names);
    }

    @Test
    void pressesTheChosenAction() {
        AtomicBoolean rich = new AtomicBoolean();
        Goal gold = metWhen(rich);
        on("SHOP", "sell", Do.press(click("sell")).serves(gold));
        FakeGame game = new FakeGame();
        game.effects.put("SHOP.sell", () -> rich.set(true));
        DoneRecords records = records();

        End end = run(Plan.of(Plan.tier(gold)), game, records).run();

        assertEquals(End.MET, end);
        assertEquals(List.of("walk SHOP", "press SHOP.sell"), game.did, "walked there, pressed once, nothing else");
        assertTrue(records.last("SHOP.sell").orElseThrow().held(), "its record kept, as worked");
        assertTrue(game.said.getFirst().startsWith("GOAL → SHOP.sell (plain, 1 screen"), game.said.getFirst());
    }

    @Test
    void absentOnArrivalReplans() {
        AtomicBoolean rich = new AtomicBoolean();
        Goal gold = metWhen(rich);
        on("SHOP", "sell", Do.press(click("sell")).serves(gold).first());
        on("ARENA", "fight", Do.press(click("fight")).serves(gold));
        FakeGame game = new FakeGame();
        game.results.put("SHOP.sell", Pressed.Result.ABSENT);
        game.effects.put("ARENA.fight", () -> rich.set(true));
        DoneRecords records = records();

        End end = run(Plan.of(Plan.tier(gold)), game, records).run();

        assertEquals(End.MET, end);
        assertEquals(List.of("walk SHOP", "press SHOP.sell", "walk ARENA", "press ARENA.fight"), game.did,
                "the first choice not there, the next one taken; neither throws");
        assertTrue(records.last("SHOP.sell").isEmpty(), "nothing was pressed: no record");
        assertTrue(game.said.stream().anyMatch(line -> line.startsWith("SHOP.sell was not on its screen on arrival")),
                String.join("\n", game.said));
    }

    @Test
    void idlesUntilDue() {
        AtomicBoolean claimed = new AtomicBoolean();
        Goal chest = metWhen(claimed);
        on("HOME", "chest", Do.press(click("chest")).serves(chest).every(Duration.ofHours(2)));
        FakeGame game = new FakeGame();
        DoneRecords records = records();
        records.done("HOME.chest", true);
        game.effects.put("HOME.chest", () -> claimed.set(true));
        PlanRun run = run(Plan.of(Plan.tier(chest)), game, records);

        assertEquals(Optional.empty(), run.round());

        assertEquals(List.of(Duration.ofHours(2)), game.idled, "one wait, until the chest comes due");
        assertTrue(game.did.isEmpty(), "nothing walked or pressed while waiting");
        assertEquals(End.MET, run.run());
        assertEquals(List.of("press HOME.chest"), game.did);
    }

    @Test
    void failedCheckRetriesLater() {
        AtomicBoolean rich = new AtomicBoolean();
        Goal gold = metWhen(rich);
        on("HOME", "sell", Do.press(click("sell")).serves(gold).checks(Condition.of(rich::get))
                .onFail(Retry.later(Duration.ofMinutes(10))));
        FakeGame game = new FakeGame();
        DoneRecords records = records();
        PlanRun run = run(Plan.of(Plan.tier(gold)), game, records);

        run.round();
        assertTrue(records.last("HOME.sell").map(done -> !done.held()).orElse(false), "kept as failed");
        assertTrue(game.said.stream().anyMatch(line -> line.startsWith("HOME.sell did not work: its check said no;"
                + " tried again at 2026-11-06 12:10")), String.join("\n", game.said));

        game.effects.put("HOME.sell", () -> rich.set(true));
        run.round();
        assertEquals(List.of(Duration.ofMinutes(10)), game.idled, "waits for its retry, nothing pressed meanwhile");
        assertEquals(End.MET, run.run());
        assertEquals(List.of("press HOME.sell", "press HOME.sell"), game.did, "a bot method's check reads no fact");
    }

    @Test
    void unknownFactReadFirst() throws ReflectiveOperationException {
        // Unknown whatever the level reads; the level is what it reads.
        Goal hero = Goal.of(Condition.any(() -> Answer.UNKNOWN, Need.atLeast(Hero::level, 1_000_000)));
        on("ARENA", "fight", Do.press(click("fight")).serves(hero).once());
        FakeGame game = new FakeGame();
        Method level = Hero.class.getMethod("level");
        game.feeds.put(level, List.of("SHOP"));
        PlanRun run = run(Plan.of(Plan.tier(hero)), game, records());

        run.round();
        assertEquals(List.of("walk SHOP"), game.did, "SHOP is one step off the way to ARENA: read there first");
        assertTrue(game.said.getFirst().startsWith("level is unknown: read it on SHOP first"), game.said.getFirst());

        run.round();
        run.round();
        assertEquals(List.of("walk SHOP", "walk ARENA", "press ARENA.fight", "read ARENA"), game.did,
                "gone to read once, then the action, unknown still counting as possible");
    }

    @Test
    void aFactNoReadoutFeedsIsNeverGoneToRead() {
        Goal hero = Goal.of(Condition.any(() -> Answer.UNKNOWN, Need.atLeast(Hero::level, 1_000_000)));
        on("ARENA", "fight", Do.press(click("fight")).serves(hero).once());
        FakeGame game = new FakeGame();

        run(Plan.of(Plan.tier(hero)), game, records()).round();

        assertEquals(List.of("walk ARENA", "press ARENA.fight", "read ARENA"), game.did,
                "the goal's fact read again where the press left it, never gone to");
    }

    @Test
    void aGoalNothingCanEverDoStopsThePlan() {
        AtomicBoolean never = new AtomicBoolean();
        Goal stuck = metWhen(never);
        on("HOME", "once", Do.press(click("once")).serves(stuck).once());
        FakeGame game = new FakeGame();
        DoneRecords records = records();
        records.done("HOME.once", true);

        End end = run(Plan.of(Plan.tier(stuck)), game, records).run();

        assertEquals(End.STUCK, end);
        assertTrue(game.did.isEmpty());
        assertTrue(game.said.getLast().startsWith("Nothing can be done for GOAL (HOME.once blocked: done for good)"),
                game.said.getLast());
    }

    @Test
    void aPressLeftOnAnotherScreenWorksWhenItsCheckHolds() {
        AtomicBoolean rich = new AtomicBoolean();
        Goal gold = metWhen(rich);
        on("HOME", "sell", Do.press(click("sell")).serves(gold).checks(Condition.of(rich::get)));
        FakeGame game = new FakeGame();
        game.results.put("HOME.sell", Pressed.Result.LEFT);
        game.effects.put("HOME.sell", () -> rich.set(true));
        DoneRecords records = records();

        assertEquals(End.MET, run(Plan.of(Plan.tier(gold)), game, records).run());

        assertTrue(records.last("HOME.sell").orElseThrow().held(), "a check that holds wins over the screen left on");
    }

    @Test
    void aStuckWalkLeavesTheActionBe() {
        AtomicBoolean rich = new AtomicBoolean();
        Goal gold = metWhen(rich);
        on("SHOP", "sell", Do.press(click("sell")).serves(gold));
        FakeGame game = new FakeGame() {
            @Override
            public boolean walk(String screen) {
                did.add("walk " + screen);
                throw new IllegalStateException("stuck");
            }
        };
        PlanRun run = run(Plan.of(Plan.tier(gold)), game, records());

        assertThrows(IllegalStateException.class, run::round, "the throw goes on to the run's recovery");
        run.round();

        assertEquals(List.of("walk SHOP"), game.did, "not walked into again at once");
        assertEquals(List.of(PlanRun.HELD_OFF), game.idled);
    }

    @Test
    void aReadingNotReachedIsNoDetour() {
        Goal hero = Goal.of(Condition.any(() -> Answer.UNKNOWN, Need.atLeast(Hero::level, 1_000_000)));
        on("ARENA", "fight", Do.press(click("fight")).serves(hero).once());
        FakeGame game = new FakeGame() {
            @Override
            public boolean walk(String screen) {
                if (screen.equals("SHOP")) {
                    did.add("refused SHOP");
                    return false;
                }
                return super.walk(screen);
            }
        };
        game.feeds.put(levelMethod(), List.of("SHOP"));

        run(Plan.of(Plan.tier(hero)), game, records()).round();

        assertEquals(List.of("refused SHOP", "walk ARENA", "press ARENA.fight", "read ARENA"), game.did,
                "the way to the reading shut: the action is done this round");
    }

    private static Method levelMethod() {
        try {
            return Hero.class.getMethod("level");
        } catch (NoSuchMethodException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void anActionNotReachedIsLeftBe() {
        AtomicBoolean rich = new AtomicBoolean();
        Goal gold = metWhen(rich);
        on("SHOP", "sell", Do.press(click("sell")).serves(gold));
        FakeGame game = new FakeGame() {
            @Override
            public boolean walk(String screen) {
                did.add("walk " + screen);
                return false;
            }
        };
        PlanRun run = run(Plan.of(Plan.tier(gold)), game, records());

        run.round();
        run.round();

        assertEquals(List.of("walk SHOP"), game.did, "not walked to again at once");
        assertEquals(List.of(PlanRun.HELD_OFF), game.idled, "it waits out the hold instead");
    }

    @Test
    void aPressThatMeetsNothingIsLeftBeAfterThreeInARow() {
        Goal never = metWhen(new AtomicBoolean());
        on("HOME", "tap", Do.press(click("tap")).serves(never));
        FakeGame game = new FakeGame();
        PlanRun run = run(Plan.of(Plan.tier(never)), game, records());

        for (int i = 0; i < PlanRun.REPEATS; i++) run.round();
        assertEquals(List.of("press HOME.tap", "press HOME.tap", "press HOME.tap"), game.did);
        assertTrue(game.said.getLast().startsWith("HOME.tap was pressed 3 times in a row and GOAL is still not met"),
                game.said.getLast());
        assertTrue(game.idled.stream().allMatch(PlanRun.PRESS_GAP::equals) && game.idled.size() == 2,
                "a second between presses, none before the first: " + game.idled);

        run.round();
        assertEquals(3, game.did.size(), "left be: nothing pressed");
        assertEquals(PlanRun.HELD_OFF, game.idled.getLast());
    }

    @Test
    void aNeedSayingNoWaitsInsteadOfStopping() {
        AtomicInteger gems = new AtomicInteger(10);
        AtomicBoolean full = new AtomicBoolean();
        Goal energy = metWhen(full);
        on("HOME", "buyEnergy", Do.press(click("buyEnergy")).serves(energy)
                .needs(Condition.of(() -> gems.get() >= 50)));
        FakeGame game = new FakeGame();
        game.effects.put("HOME.buyEnergy", () -> full.set(true));
        PlanRun run = run(Plan.of(Plan.tier(energy)), game, records());

        assertEquals(Optional.empty(), run.round(), "too few gems for now: no end");
        assertEquals(List.of(Planner.RECHECK), game.idled, "waits, then looks again");
        assertTrue(game.did.isEmpty());

        gems.set(60);
        assertEquals(End.MET, run.run());
        assertEquals(List.of("press HOME.buyEnergy"), game.did);
    }

    @Test
    void aTooDeepGoalBelowDoesNotStopTheTierAbove() {
        AtomicBoolean rich = new AtomicBoolean();
        Goal gold = metWhen(rich);
        on("HOME", "sell", Do.press(click("sell")).serves(gold));
        Goal[] deep = new Goal[6];
        for (int i = 0; i < deep.length; i++) deep[i] = metWhen(new AtomicBoolean());
        on("HOME", "last", Do.press(click("last")).serves(deep[5]));
        for (int i = 0; i < 5; i++) on("HOME", "a" + i, Do.press(click("a" + i)).serves(deep[i]).needs(deep[i + 1]));
        FakeGame game = new FakeGame();
        game.effects.put("HOME.sell", () -> rich.set(true));
        PlanRun run = run(Plan.of(Plan.tier(gold), Plan.tier(deep[0])), game, records());

        assertEquals(Optional.empty(), run.round(), "the tier above acts");
        assertEquals(List.of("press HOME.sell"), game.did);
        assertEquals(Optional.of(End.TOO_DEEP), run.round(), "then the tier that is too deep stops the plan");
    }
}
