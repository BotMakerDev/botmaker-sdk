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
import com.botmaker.sdk.internal.bot.BotStuckException;
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
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
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
        /** Each wait, the slices of one idle (each followed by a look, nothing done between) summed as one. */
        final List<Duration> idled = new ArrayList<>();
        /** When each fact was last read; one read on a screen is read when that screen is read again. */
        final Map<Method, Instant> readAt = new HashMap<>();
        /** The screens each fact is read on when that screen is read; empty: by {@link #feeds}. */
        final Map<Method, List<String>> readOn = new HashMap<>();
        int looks;
        private boolean looked;
        /** What the next watchdog checkpoint throws, once: a restart Android's dialogs owe. */
        RuntimeException owed;
        int checkpoints;

        @Override
        public void checkpoint() {
            checkpoints++;
            RuntimeException thrown = owed;
            owed = null;
            if (thrown != null) throw thrown;
        }

        @Override
        public Optional<String> where() {
            return Optional.ofNullable(at);
        }

        @Override
        public boolean walk(String screen) {
            did.add("walk " + screen);
            at = screen;
            looked = false;
            tick();
            return true;
        }

        @Override
        public Pressed.Result press(Step step) {
            did.add("press " + step.key());
            looked = false;
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
            looked = false;
            for (Method fact : facts) {
                if (readOn.getOrDefault(fact, feeds.getOrDefault(fact, List.of())).contains(screen)) {
                    readAt.put(fact, clock.now);
                }
            }
        }

        @Override
        public Optional<Instant> readAt(Method fact) {
            return Optional.ofNullable(readAt.get(fact));
        }

        @Override
        public boolean look() {
            looks++;
            looked = true;
            return false;
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
            if (looked) idled.set(idled.size() - 1, idled.getLast().plus(time));
            else idled.add(time);
            looked = false;
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

        assertEquals(List.of(PlanRun.IDLE_LOOK), game.idled, "a slice of the wait, then a look at the game");
        assertEquals(1, game.looks);
        assertTrue(game.did.isEmpty(), "nothing walked or pressed while waiting");
        assertEquals(End.MET, run.run());
        assertEquals(List.of("press HOME.chest"), game.did);
        assertEquals(List.of(Duration.ofHours(2)), game.idled, "one wait in all, until the chest comes due");
        assertEquals(Duration.ofHours(2).dividedBy(PlanRun.IDLE_LOOK), game.looks, "looked again every slice");
        assertEquals(1, game.said.stream().filter(line -> line.startsWith("Every goal is met or waiting")).count(),
                "the idle said once, not each slice: " + game.said);
    }

    @Test
    void anIdleLookSeesAGoalFallBackUnmet() {
        AtomicBoolean full = new AtomicBoolean(true);
        Goal energy = metWhen(full);
        AtomicBoolean claimed = new AtomicBoolean();
        Goal chest = metWhen(claimed);
        on("HOME", "chest", Do.press(click("chest")).serves(chest).every(Duration.ofHours(2)));
        on("HOME", "refill", Do.press(click("refill")).serves(energy));
        FakeGame game = new FakeGame() {
            @Override
            public boolean look() {
                super.look();
                // What the look reads: the energy spent meanwhile.
                if (looks == 2) full.set(false);
                return false;
            }
        };
        game.effects.put("HOME.refill", () -> full.set(true));
        DoneRecords records = records();
        records.done("HOME.chest", true);
        PlanRun run = run(Plan.of(Plan.tier(energy), Plan.tier(chest)), game, records);

        run.round();
        run.round();
        run.round();

        assertEquals(List.of("press HOME.refill"), game.did, "seen by the second look, long before the chest is due");
        assertEquals(List.of(PlanRun.IDLE_LOOK.multipliedBy(2)), game.idled);
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
        assertEquals(List.of(PlanRun.IDLE_LOOK), game.idled, "waits for its retry, nothing pressed meanwhile");
        assertEquals(End.MET, run.run());
        assertEquals(List.of("press HOME.sell", "press HOME.sell"), game.did, "a bot method's check reads no fact");
        assertEquals(List.of(Duration.ofMinutes(10).minusSeconds(1)), game.idled,
                "one wait in all, until its retry, timed from the press's start, a second before it ended");
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
        assertEquals(List.of(PlanRun.IDLE_LOOK), game.idled, "it waits out the hold, looking again meanwhile");
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
        assertEquals(List.of(PlanRun.IDLE_LOOK), game.idled, "it waits out the hold instead, looking again meanwhile");
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
        assertEquals(PlanRun.IDLE_LOOK, game.idled.getLast());
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
        assertEquals(List.of(PlanRun.IDLE_LOOK), game.idled, "waits, then looks again");
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

    @Test
    void aPressThatLeavesTheMapIsUnprovenUntilItsCheckIsRead() {
        Goal strong = Goal.of(Need.atLeast(Hero::level, 5));
        on("ARENA", "train", Do.press(click("train")).serves(strong).checks(Need.atLeast(Hero::level, 5)).once());
        FakeGame game = new FakeGame();
        game.feeds.put(levelMethod(), List.of("HOME"));
        // The press leaves the game on no screen of the map.
        game.effects.put("ARENA.train", () -> game.at = null);
        DoneRecords records = records();
        PlanRun run = run(Plan.of(Plan.tier(strong)), game, records);

        run.round();
        assertEquals(List.of("walk ARENA", "press ARENA.train"), game.did, "nothing read off no screen");
        assertTrue(records.last("ARENA.train").isEmpty(), "no success kept on a reading older than the press");
        assertTrue(game.said.getLast().startsWith("ARENA.train pressed, unproven: its check reads level"),
                game.said.getLast());

        run.round();
        assertEquals(List.of("walk ARENA", "press ARENA.train", "walk HOME", "read HOME"), game.did,
                "the next round reads the check's fact where it is fed before it plans");
        assertTrue(records.last("ARENA.train").orElseThrow().held(), "kept once read after the press");
        assertEquals(NOON.plusSeconds(1), records.last("ARENA.train").orElseThrow().at(),
                "timed by the press, not by the proof");
    }

    @Test
    void anUnprovenPressNothingCanReadKeepsNoRecordAndStopsAtTheCap() {
        Goal strong = Goal.of(Need.atLeast(Hero::level, 5));
        on("HOME", "train", Do.press(click("train")).serves(strong).checks(Need.atLeast(Hero::level, 5)));
        FakeGame game = new FakeGame();
        DoneRecords records = records();
        PlanRun run = run(Plan.of(Plan.tier(strong)), game, records);

        for (int i = 0; i < PlanRun.REPEATS + 1; i++) run.round();

        assertEquals(PlanRun.REPEATS, game.did.stream().filter("press HOME.train"::equals).count(), game.did.toString());
        assertTrue(records.last("HOME.train").isEmpty(), "never proven, never kept");
        assertTrue(game.said.stream().anyMatch(line -> line.startsWith("HOME.train's check reads level, which could"
                + " not be read again since its press: no record kept")), String.join("\n", game.said));
        assertTrue(game.said.stream().anyMatch(line -> line.startsWith("HOME.train was pressed 3 times in a row and"
                + " its check could not be read after any")), String.join("\n", game.said));
    }

    /** HOME.train, whose check reads a fact no screen feeds: pressed, never proven, never progress. */
    private PlanRun trainingNothingProves(FakeGame game, Duration stuckAfter) {
        Goal strong = Goal.of(Need.atLeast(Hero::level, 5));
        on("HOME", "train", Do.press(click("train")).serves(strong).checks(Need.atLeast(Hero::level, 5)));
        return new PlanRun(Plan.of(Plan.tier(strong)), map(), records(), game, goal -> "GOAL", stuckAfter);
    }

    @Test
    void actingWithNoProgressIsStuckAtStuckAfterAndHoldsTheActionOff() {
        FakeGame game = new FakeGame();
        PlanRun run = trainingNothingProves(game, Duration.ofSeconds(3));

        assertEquals(Optional.empty(), run.round(), "one second of pressing: not yet");
        BotStuckException stuck = assertThrows(BotStuckException.class, run::round);

        assertTrue(stuck.getMessage().startsWith("No progress for 0 min"), stuck.getMessage());
        assertEquals(2, game.did.stream().filter("press HOME.train"::equals).count(), game.did.toString());
        assertEquals(2, game.checkpoints, "the watchdog looked once a round that acted");
        run.round();
        assertEquals(2, game.did.stream().filter("press HOME.train"::equals).count(),
                "held off: the recovered run does not go straight back to it");
    }

    @Test
    void aScreenChangeIsProgress() {
        FakeGame game = new FakeGame();
        PlanRun run = trainingNothingProves(game, Duration.ofSeconds(3));

        run.round();
        game.at = "SHOP";

        assertDoesNotThrow(run::round, "another screen than last round's: the clock starts over");
        assertTrue(game.did.contains("walk HOME"), game.did.toString());
    }

    @Test
    void goingBackAndForthBetweenTwoScreensIsNoProgress() {
        FakeGame game = new FakeGame();
        PlanRun run = trainingNothingProves(game, Duration.ofSeconds(3));

        run.round();
        game.at = "SHOP";
        run.round();
        game.at = "SHOP";

        assertThrows(BotStuckException.class, run::round, "SHOP again: a screen seen since the last progress");
    }

    @Test
    void aRestartOwedWhileIdlingIsTakenAtOnce() {
        AtomicBoolean claimed = new AtomicBoolean();
        Goal chest = metWhen(claimed);
        on("HOME", "chest", Do.press(click("chest")).serves(chest).every(Duration.ofHours(2)));
        FakeGame game = new FakeGame();
        DoneRecords records = records();
        records.done("HOME.chest", true);
        PlanRun run = run(Plan.of(Plan.tier(chest)), game, records);
        game.owed = new BotStuckException("The game crashed");

        assertThrows(BotStuckException.class, run::round, "not two hours later, at the next press");
        assertEquals(List.of(PlanRun.IDLE_LOOK), game.idled);
    }

    @Test
    void aRestartTheGameOwesIsTakenInAPlanRun() {
        FakeGame game = new FakeGame();
        PlanRun run = trainingNothingProves(game, Duration.ofMinutes(10));
        BotStuckException owed = new BotStuckException("The game stopped answering");
        game.owed = owed;

        assertSame(owed, assertThrows(BotStuckException.class, run::round), "to the run's recovery, which restarts");
        assertEquals(List.of("press HOME.train", "read HOME"), game.did, "after the round's press and its reading");
    }

    @Test
    void aPressWhoseCheckIsReadWhereItLeftIsKeptAtOnce() {
        Goal strong = Goal.of(Need.atLeast(Hero::level, 5));
        on("ARENA", "train", Do.press(click("train")).serves(strong).checks(Need.atLeast(Hero::level, 5)).once());
        FakeGame game = new FakeGame();
        game.feeds.put(levelMethod(), List.of("ARENA"));
        DoneRecords records = records();

        run(Plan.of(Plan.tier(strong)), game, records).round();

        assertTrue(records.last("ARENA.train").orElseThrow().held(), "read again on ARENA after the press: proven");
    }

    @Test
    void anActionOnTheWayFirstOnArrivalLeavesTheActionAChoice() {
        AtomicBoolean rich = new AtomicBoolean();
        Goal gold = metWhen(rich);
        AtomicBoolean open = new AtomicBoolean();
        AtomicBoolean got = new AtomicBoolean();
        Goal bonus = metWhen(got);
        on("SHOP", "sell", Do.press(click("sell")).serves(gold));
        // Only arriving shows the bonus can be taken.
        on("SHOP", "bonus", Do.press(click("bonus")).serves(bonus).needs(Condition.of(open::get)).first());
        FakeGame game = new FakeGame() {
            @Override
            public boolean walk(String screen) {
                open.set(true);
                return super.walk(screen);
            }
        };
        game.effects.put("SHOP.sell", () -> rich.set(true));
        game.effects.put("SHOP.bonus", () -> got.set(true));

        End end = run(Plan.of(Plan.tier(gold), Plan.tier(bonus)), game, records()).run();

        assertEquals(End.MET, end);
        assertEquals(List.of("walk SHOP", "press SHOP.bonus", "press SHOP.sell"), game.did,
                "the bonus first, then the action the walk came for, never left be");
        assertTrue(game.said.stream().noneMatch(line -> line.contains("left be")), String.join("\n", game.said));
        assertTrue(game.said.contains("SHOP.bonus goes first once there; SHOP.sell after it."),
                String.join("\n", game.said));
    }

    /** A press that held counts its goal of facts met until one is read: the tier below goes on, nothing pressed twice. */
    @Test
    void aGoalOfFactsCountsAsMetAfterAPressUntilItsFactIsRead() throws ReflectiveOperationException {
        Goal hero = Goal.of(Need.atLeast(Hero::level, 1_000_000));
        AtomicBoolean tapped = new AtomicBoolean();
        Goal below = metWhen(tapped);
        on("ARENA", "fight", Do.press(click("fight")).serves(hero));
        // A last resort, so it is not done on the way to the fight.
        on("HOME", "tap", Do.press(click("tap")).serves(below).last());
        FakeGame game = new FakeGame();
        game.effects.put("HOME.tap", () -> tapped.set(true));
        PlanRun run = run(Plan.of(Plan.tier(hero), Plan.tier(below)), game, records());

        run.round();
        run.round();
        assertEquals(List.of("walk ARENA", "press ARENA.fight", "read ARENA", "walk HOME", "press HOME.tap"), game.did,
                "the fight assumed to have done its work: the tier below next, the fight not pressed again");

        run.round();
        assertTrue(game.said.getLast().startsWith("Every goal is met or waiting, GOAL only as assumed after a press"),
                game.said.getLast());
        run.round();
        assertEquals(List.of("press ARENA.fight", "read ARENA"), game.did.subList(game.did.size() - 2, game.did.size()),
                "planned again from what the game says: not met");

        // Its fact read after the press: what was read rules at once.
        game.readAt.put(Hero.class.getMethod("level"), clock.now.plusSeconds(1));
        clock.now = clock.now.plusSeconds(2);
        long presses = game.did.stream().filter("press ARENA.fight"::equals).count();
        run.round();
        assertEquals(presses + 1, game.did.stream().filter("press ARENA.fight"::equals).count(),
                "the reading says no: pressed again");
    }

    /** A reading taken while the action still takes its time shows the game before its effect: the goal stays met. */
    @Test
    void aReadingWhileTheActionTakesItsTimeEndsNoAssumption() throws ReflectiveOperationException {
        Goal hero = Goal.of(Need.atLeast(Hero::level, 1_000_000));
        AtomicBoolean tapped = new AtomicBoolean();
        Goal below = metWhen(tapped);
        on("ARENA", "fight", Do.press(click("fight")).serves(hero).takes(Duration.ofSeconds(40)));
        on("HOME", "tap", Do.press(click("tap")).serves(below).last());
        FakeGame game = new FakeGame();
        game.readOn.put(Hero.class.getMethod("level"), List.of("ARENA"));
        game.effects.put("HOME.tap", () -> tapped.set(true));
        PlanRun run = run(Plan.of(Plan.tier(hero), Plan.tier(below)), game, records());

        run.round();
        assertTrue(game.readAt.containsKey(Hero.class.getMethod("level")), "read where the fight left the game");
        run.round();
        assertEquals(List.of("walk ARENA", "press ARENA.fight", "read ARENA", "walk HOME", "press HOME.tap"), game.did,
                "read mid-fight: the fight not pressed again");
    }

    /** An action done for good leaves a goal no fact tells as its check says: nothing assumed for the rest of the run. */
    @Test
    void anActionDoneForGoodAssumesNothingOfAGoalOfNoFact() {
        Goal never = metWhen(new AtomicBoolean());
        on("HOME", "tap", Do.press(click("tap")).serves(never).once());
        FakeGame game = new FakeGame();
        PlanRun run = run(Plan.of(Plan.tier(never)), game, records());

        run.round();

        assertEquals(Optional.of(End.STUCK), run.round(), "unmet, its action done: stuck at once, not hours later");
        assertTrue(game.said.stream().noneMatch(line -> line.contains("only as assumed")), String.join("\n", game.said));
    }

    /** A goal no fact tells, served by an action with a cadence, counts as met until the action comes due again. */
    @Test
    void aGoalOfNoFactCountsAsMetUntilItsActionIsDue() {
        Goal never = metWhen(new AtomicBoolean());
        on("HOME", "tap", Do.press(click("tap")).serves(never).every(Duration.ofHours(1)));
        FakeGame game = new FakeGame();
        PlanRun run = run(Plan.of(Plan.tier(never)), game, records());

        run.round();
        run.round();
        assertTrue(game.said.getLast().startsWith("Every goal is met or waiting, GOAL only as assumed after a press"),
                "the plan does not end on an assumption: " + game.said.getLast());
        assertEquals(Optional.empty(), run.round(), "planned again: the tap waits for its hour");
        assertEquals(List.of("press HOME.tap"), game.did);
        assertEquals(List.of(PlanRun.IDLE_LOOK), game.idled);
    }
}
