package com.botmaker.sdk.internal.plan;

import com.botmaker.sdk.api.plan.Goal;
import com.botmaker.sdk.api.plan.Plan;
import com.botmaker.sdk.api.input.Key;
import com.botmaker.sdk.api.screens.Answer;
import com.botmaker.sdk.api.screens.Button;
import com.botmaker.sdk.api.screens.Condition;
import com.botmaker.sdk.api.screens.Do;
import com.botmaker.sdk.api.screens.Input;
import com.botmaker.sdk.api.screens.Retry;
import com.botmaker.sdk.api.screens.Scroll;
import com.botmaker.sdk.api.screens.Setup;
import com.botmaker.sdk.api.screens.Strip;
import com.botmaker.sdk.internal.plan.Round.Block;
import com.botmaker.sdk.internal.plan.Round.Step;
import com.botmaker.sdk.internal.plan.Round.Why;
import com.botmaker.sdk.internal.screens.MapScreen;
import com.botmaker.sdk.internal.screens.ScreenGraph;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

import static com.botmaker.sdk.internal.screens.MapFixtures.click;
import static com.botmaker.sdk.internal.screens.MapFixtures.key;
import static com.botmaker.sdk.internal.screens.MapFixtures.marks;
import static com.botmaker.sdk.internal.screens.MapFixtures.ref;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** No screen and no run: a map, a plan and a file of records in, a choice out. */
class PlannerTest {

    private static final Instant NOON = Instant.parse("2026-11-06T12:00:00Z");

    @TempDir
    Path dir;

    private final Map<String, Map<String, Do>> placed = new LinkedHashMap<>();

    /** Holds {@code action} as the field {@code field} of {@code screen}. */
    private Do on(String screen, String field, Do action) {
        placed.computeIfAbsent(screen, s -> new LinkedHashMap<>()).put(field, action);
        return action;
    }

    private static Do press(String id) {
        return Do.press(click(id));
    }

    /** A goal of its own: goals on the very same check are one goal, and a lambda capturing nothing is one instance. */
    private static Goal unmet() {
        return Goal.of(new Condition() {
            @Override
            public Answer answer() {
                return Answer.NO;
            }
        });
    }

    private static Goal met() {
        return Goal.of((Condition) () -> Answer.YES);
    }

    /**
     * HOME opens SHOP and ARENA, SHOP opens VAULT, VAULT opens CRYPT, each with its way back, ARENA farther than SHOP by
     * what it takes to show; each holds what {@link #on} put there.
     */
    private ScreenGraph map() {
        Map<String, MapScreen> screens = new LinkedHashMap<>();
        screens.put("HOME", screen(Setup.named("HOME").start(), "HOME", Map.of("shop", click("shop"), "arena", click("arena"))));
        screens.put("SHOP", screen(Setup.named("SHOP").from(ref("HOME", "shop")), "SHOP", Map.of("vault", click("vault"))));
        screens.put("ARENA", screen(Setup.named("ARENA").from(ref("HOME", "arena")).takes(Duration.ofSeconds(9)), "ARENA", Map.of()));
        screens.put("VAULT", screen(Setup.named("VAULT").from(ref("SHOP", "vault")), "VAULT", Map.of("crypt", click("crypt"))));
        screens.put("CRYPT", screen(Setup.named("CRYPT").from(ref("VAULT", "crypt")), "CRYPT", Map.of()));
        return ScreenGraph.of(screens);
    }

    private MapScreen screen(Setup setup, String constant, Map<String, Input> ways) {
        Map<String, Input> inputs = new LinkedHashMap<>(ways);
        Map<String, Do> actions = placed.getOrDefault(constant, Map.of());
        actions.forEach((field, action) -> inputs.put(field, action.input()));
        return new MapScreen(setup, inputs, Map.of(), Map.of(), actions);
    }

    /** Records {@code key} done {@code ago} before noon. */
    private void done(String key, Duration ago, boolean held) {
        new DoneRecords(dir.resolve("done.json"), Clock.fixed(NOON.minus(ago), ZoneOffset.UTC)).done(key, held);
    }

    private DoneRecords records() {
        return new DoneRecords(dir.resolve("done.json"), Clock.fixed(NOON, ZoneOffset.UTC));
    }

    private PlanModel model(Plan plan, String at) {
        return new PlanModel(plan, map(), records(), at);
    }

    private Round round(Plan plan) {
        return Planner.round(model(plan, "HOME"));
    }

    private static List<String> keys(List<Step> steps) {
        return steps.stream().map(Step::key).toList();
    }

    /** What the round says of the action {@code key}. */
    private static Why why(Round round, String key) {
        return round.considered().stream().filter(c -> c.step().key().equals(key)).findFirst().orElseThrow().why();
    }

    @Test
    void tierOrderIsStrict() {
        Goal top = unmet();
        Goal below = unmet();
        on("ARENA", "fight", press("fight").serves(top));
        on("SHOP", "sell", press("sell").serves(below));

        Round round = round(Plan.of(Plan.tier(top), Plan.tier(below)));

        assertEquals(OptionalInt.of(0), round.tier());
        assertEquals(List.of("ARENA.fight"), keys(round.route()), "the nearer action of the tier below waits");
    }

    @Test
    void aGoalNothingCanDoHoldsItsTier() {
        Goal served = unmet();
        Goal below = unmet();
        on("SHOP", "sell", press("sell").serves(below));

        Round round = round(Plan.of(Plan.tier(served), Plan.tier(below)));

        assertEquals(OptionalInt.of(0), round.tier(), "neither met nor waiting");
        assertEquals(List.of(), round.route());
        assertEquals(Optional.empty(), round.due());
    }

    @Test
    void tierGoalsShareARoute() {
        Goal far = unmet();
        Goal alsoFar = unmet();
        Goal near = unmet();
        on("VAULT", "open", press("open").serves(far, alsoFar));
        on("SHOP", "sell", press("sell").serves(near));

        Round round = round(Plan.of(Plan.tier(far, alsoFar, near)));

        assertEquals(List.of("SHOP.sell", "VAULT.open"), keys(round.route()), "nearest next, one action once");
    }

    @Test
    void bucketsThenOverThenCost() {
        Goal cheapest = unmet();
        on("VAULT", "far", press("far").serves(cheapest));
        on("SHOP", "near", press("near").serves(cheapest));
        assertEquals(List.of("SHOP.near"), keys(round(Plan.of(Plan.tier(cheapest))).route()), "cost");

        placed.clear();
        Goal overIt = unmet();
        Do near = on("SHOP", "near", press("near").serves(overIt));
        on("VAULT", "far", press("far").serves(overIt).over(() -> near));
        assertEquals(List.of("VAULT.far"), keys(round(Plan.of(Plan.tier(overIt))).route()), ".over beats cost");

        placed.clear();
        Goal bucket = unmet();
        Do plain = on("SHOP", "plain", press("plain").serves(bucket));
        on("VAULT", "first", press("first").serves(bucket).first());
        on("ARENA", "last", press("last").serves(bucket).last().over(() -> plain));
        Round buckets = round(Plan.of(Plan.tier(bucket)));
        assertEquals(List.of("VAULT.first"), keys(buckets.route()), "a bucket beats cost");
        assertEquals(List.of("VAULT.first", "SHOP.plain", "ARENA.last"),
                buckets.considered().stream().map(c -> c.step().key()).toList(), "and .over, across buckets");
        assertInstanceOf(Why.LastResort.class, why(buckets, "ARENA.last"));
    }

    @Test
    void lastResortIsTakenWhenNothingElseCanBe() {
        Goal goal = unmet();
        on("SHOP", "plain", press("plain").serves(goal).needs((Condition) () -> Answer.NO));
        on("ARENA", "last", press("last").serves(goal).last());

        assertEquals(List.of("ARENA.last"), keys(round(Plan.of(Plan.tier(goal))).route()));
    }

    @Test
    void overCycleRefused() {
        Goal goal = unmet();
        Do[] back = new Do[1];
        Do first = on("SHOP", "first", press("first").serves(goal).over(() -> back[0]));
        back[0] = on("VAULT", "second", press("second").serves(goal).over(() -> first));

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> model(Plan.of(Plan.tier(goal)), "HOME"));

        assertTrue(refused.getMessage().contains("SHOP.first over VAULT.second over SHOP.first"), refused.getMessage());
    }

    @Test
    void overPassesThroughAnActionNotAChoice() {
        Goal goal = unmet();
        Do near = on("SHOP", "near", press("near").serves(goal));
        Do between = on("HOME", "between", press("between").over(() -> near));
        on("VAULT", "far", press("far").serves(goal).over(() -> between));

        assertEquals(List.of("VAULT.far"), keys(round(Plan.of(Plan.tier(goal))).route()));
    }

    @Test
    void unmetGoalNeedBecomesSubGoal() {
        Goal gold = unmet();
        Goal sword = unmet();
        on("VAULT", "earn", press("earn").serves(gold));
        on("SHOP", "buy", press("buy").serves(sword).needs(gold));

        Round round = round(Plan.of(Plan.tier(sword)));

        assertEquals(List.of("VAULT.earn", "SHOP.buy"), keys(round.route()), "what it needs first, though farther");
        assertEquals(new Why.Needs(gold, 1), why(round, "SHOP.buy"));
    }

    @Test
    void anUnmetConditionOnlyBlocks() {
        Goal goal = unmet();
        on("SHOP", "spend", press("spend").serves(goal).needs((Condition) () -> Answer.NO));

        Round round = round(Plan.of(Plan.tier(goal)));

        assertEquals(List.of(), round.route());
        assertEquals(new Why.Blocked(Block.CONDITION), why(round, "SHOP.spend"));
    }

    @Test
    void anActionWithNoNeedComesBeforeOneWithASubGoal() {
        Goal gold = unmet();
        Goal goal = unmet();
        on("HOME", "earn", press("earn").serves(gold));
        on("HOME", "needy", press("needy").serves(goal).needs(gold));
        on("VAULT", "free", press("free").serves(goal));

        assertEquals(List.of("VAULT.free"), keys(round(Plan.of(Plan.tier(goal))).route()));
    }

    @Test
    void subGoalDepthStopsAtFour() {
        Goal[] goals = {unmet(), unmet(), unmet(), unmet(), unmet(), unmet()};
        on("HOME", "last", press("last").serves(goals[5]));
        for (int i = 0; i < 5; i++) {
            on("HOME", "a" + i, press("a" + i).serves(goals[i]).needs(goals[i + 1]));
        }

        Round tooDeep = round(Plan.of(Plan.tier(goals[0])));
        assertEquals(List.of(), tooDeep.route(), "five deep: no choice");
        assertEquals(new Why.TooDeep(goals[5]), why(tooDeep, "HOME.a4"));
        assertEquals(new Why.CannotMeet(goals[1]), why(tooDeep, "HOME.a0"));

        Round four = round(Plan.of(Plan.tier(goals[1])));
        assertEquals(List.of("HOME.last", "HOME.a4", "HOME.a3", "HOME.a2", "HOME.a1"), keys(four.route()));
    }

    @Test
    void goalsNeedingEachOtherEndAsNoChoice() {
        Goal a = unmet();
        Goal b = unmet();
        on("HOME", "forA", press("forA").serves(a).needs(b));
        on("HOME", "forB", press("forB").serves(b).needs(a));

        assertEquals(List.of(), round(Plan.of(Plan.tier(a))).route());
    }

    @Test
    void waitingGoalIsPassedOver() {
        Goal waiting = unmet();
        Goal below = unmet();
        on("SHOP", "claim", press("claim").serves(waiting).every(Duration.ofHours(1)));
        on("HOME", "fill", press("fill").serves(below));
        done("SHOP.claim", Duration.ofMinutes(1), true);

        Round round = round(Plan.of(Plan.tier(waiting), Plan.tier(below)));

        assertEquals(OptionalInt.of(1), round.tier());
        assertEquals(List.of("HOME.fill"), keys(round.route()));
        assertEquals(Optional.of(NOON.plus(Duration.ofMinutes(59))), round.due(), "the run comes back then");
        assertEquals(new Why.Waiting(NOON.plus(Duration.ofMinutes(59))), why(round, "SHOP.claim"));
    }

    @Test
    void aRetryLaterWaitsAndOneBlockedActionDoesNotStopIt() {
        Goal goal = unmet();
        on("SHOP", "claim", press("claim").serves(goal));
        on("HOME", "spend", press("spend").serves(goal).needs((Condition) () -> Answer.NO));
        done("SHOP.claim", Duration.ofMinutes(10), false);

        Round round = round(Plan.of(Plan.tier(goal)));

        assertEquals(OptionalInt.empty(), round.tier());
        assertEquals(new Why.Waiting(NOON.plus(Duration.ofMinutes(20))), why(round, "SHOP.claim"),
                "thirty minutes by default");
        assertEquals(Optional.of(NOON.plus(Planner.RECHECK)), round.due(),
                "the need saying no is looked at again sooner, as it may change first");
    }

    @Test
    void aSubGoalWaitingMakesItsActionWait() {
        Goal sub = unmet();
        Goal goal = unmet();
        on("SHOP", "earn", press("earn").serves(sub).every(Duration.ofHours(2)));
        on("HOME", "buy", press("buy").serves(goal).needs(sub));
        done("SHOP.earn", Duration.ofHours(1), true);

        Round round = round(Plan.of(Plan.tier(goal)));

        assertEquals(OptionalInt.empty(), round.tier());
        assertEquals(new Why.Waiting(NOON.plus(Duration.ofHours(1))), why(round, "HOME.buy"));
    }

    @Test
    void allWaitingSaysWhenDue() {
        Goal one = unmet();
        Goal two = unmet();
        on("SHOP", "hourly", press("hourly").serves(one).every(Duration.ofHours(1)));
        on("HOME", "soon", press("soon").serves(two).every(Duration.ofHours(1)));
        done("SHOP.hourly", Duration.ZERO, true);
        done("HOME.soon", Duration.ofMinutes(50), true);

        Round round = round(Plan.of(Plan.tier(one), Plan.tier(two)));

        assertEquals(OptionalInt.empty(), round.tier());
        assertEquals(List.of(), round.route());
        assertEquals(Optional.of(NOON.plus(Duration.ofMinutes(10))), round.due());
    }

    @Test
    void nothingEverDueSaysSo() {
        Goal once = unmet();
        Goal gaveUp = unmet();
        on("SHOP", "once", press("once").serves(once).once());
        on("HOME", "never", press("never").serves(gaveUp).onFail(Retry.never()));
        done("SHOP.once", Duration.ofDays(1), true);
        done("HOME.never", Duration.ofDays(1), false);

        Round round = round(Plan.of(Plan.tier(once, gaveUp)));

        assertEquals(List.of(), round.route());
        assertEquals(Optional.empty(), round.due());
        assertEquals(new Why.Blocked(Block.DONE_FOR_GOOD), why(round, "SHOP.once"));
        assertEquals(new Why.Blocked(Block.GIVEN_UP), why(round, "HOME.never"));
    }

    @Test
    void whatRulesAnActionOutIsSaid() {
        Goal goal = unmet();
        on("HOME", "locked", Do.press(click("locked").never("spends gems")).serves(goal));
        on("HOME", "ended", press("ended").serves(goal).until(() -> Answer.YES));

        Round round = round(Plan.of(Plan.tier(goal)));

        assertEquals(new Why.Blocked(Block.NEVER), why(round, "HOME.locked"), "marked never: for good");
        assertEquals(new Why.Blocked(Block.ENDED), why(round, "HOME.ended"));
    }

    @Test
    void aClosedWayLeavesNoWayThere() {
        Goal goal = unmet();
        on("SHOP", "sell", press("sell").serves(goal));
        Map<String, MapScreen> screens = new LinkedHashMap<>();
        screens.put("HOME", screen(Setup.named("HOME").start(), "HOME", Map.of("shop", click("shop"))));
        screens.put("SHOP", screen(Setup.named("SHOP").from(ref("HOME", "shop")).fromWhen(() -> false), "SHOP", Map.of()));
        PlanModel model = new PlanModel(Plan.of(Plan.tier(goal)), ScreenGraph.of(screens),
                new DoneRecords(dir.resolve("done.json"), Clock.fixed(NOON, ZoneOffset.UTC)), "HOME");

        assertEquals(new Why.Blocked(Block.NO_WAY_THERE), why(Planner.round(model), "SHOP.sell"));
    }

    @Test
    void unknownCountsAsPossible() {
        Goal goal = unmet();
        on("SHOP", "maybe", press("maybe").serves(goal).needs((Condition) () -> Answer.UNKNOWN));
        Round alone = round(Plan.of(Plan.tier(goal)));
        assertEquals(List.of("SHOP.maybe"), keys(alone.route()));
        assertEquals(new Why.Unknown(), why(alone, "SHOP.maybe"));

        on("VAULT", "known", press("known").serves(goal));
        assertEquals(List.of("VAULT.known"), keys(round(Plan.of(Plan.tier(goal))).route()), "known before unknown");
    }

    @Test
    void anUnknownGoalIsWorkedOnAndAnUnknownNeedIsNoSubGoal() {
        Goal unknown = Goal.of((Condition) () -> Answer.UNKNOWN);
        Goal goal = unmet();
        on("SHOP", "read", press("read").serves(unknown));
        on("HOME", "act", press("act").serves(goal).needs(unknown));

        Round round = round(Plan.of(Plan.tier(goal)));
        assertEquals(List.of("HOME.act"), keys(round.route()), "it is not sent to meet what may be met");
        assertEquals(new Why.Unknown(), why(round, "HOME.act"));
        assertEquals(List.of("SHOP.read"), keys(round(Plan.of(Plan.tier(unknown))).route()));
    }

    @Test
    void onTheWayWithinOneScreen() {
        Goal main = unmet();
        Goal crossed = unmet();
        Goal oneOff = unmet();
        Goal twoOff = unmet();
        Goal last = unmet();
        on("ARENA", "fight", press("fight").serves(main));
        on("HOME", "here", press("here").serves(crossed));
        on("SHOP", "oneOff", press("oneOff").serves(oneOff));
        on("VAULT", "twoOff", press("twoOff").serves(twoOff));
        on("HOME", "lastResort", press("lastResort").serves(last).last());

        Round round = round(Plan.of(Plan.tier(main), Plan.tier(crossed, oneOff, twoOff, last)));

        assertEquals(List.of("ARENA.fight"), keys(round.route()));
        assertEquals(List.of("HOME.here", "SHOP.oneOff"), keys(round.onTheWay()), "a last resort waits for its turn");
    }

    @Test
    void onTheWayIsNotForWhatTheRouteAlreadyMeets() {
        Goal goal = unmet();
        on("SHOP", "better", press("better").serves(goal).first());
        on("HOME", "other", press("other").serves(goal));
        on("SHOP", "seen", press("seen").serves(unmet()).onSight());

        assertEquals(List.of(), round(Plan.of(Plan.tier(goal))).onTheWay());
    }

    @Test
    void goalMetElsewhereIsSkipped() {
        Do claim = on("SHOP", "claim", press("claim"));
        Goal claimed = Goal.done(() -> claim);
        Goal met = met();
        Goal open = unmet();
        on("SHOP", "serve", press("serve").serves(claimed, met));
        on("HOME", "work", press("work").serves(open));
        done("SHOP.claim", Duration.ofDays(1), true);

        Round round = round(Plan.of(Plan.tier(claimed, met), Plan.tier(open)));

        assertEquals(OptionalInt.of(1), round.tier(), "read from this model's records: no run, no installed map");
        assertEquals(List.of("HOME.work"), keys(round.route()));
    }

    @Test
    void aLastResortWaitsForWhatIsBetterAndOnlyWaiting() {
        Goal goal = unmet();
        on("SHOP", "claim", press("claim").serves(goal).first().every(Duration.ofHours(1)));
        on("HOME", "spend", press("spend").serves(goal).last());
        done("SHOP.claim", Duration.ofMinutes(10), true);

        Round round = round(Plan.of(Plan.tier(goal)));

        assertEquals(List.of(), round.route(), "gems are not spent minutes before the free claim");
        assertEquals(OptionalInt.empty(), round.tier());
        assertEquals(Optional.of(NOON.plus(Duration.ofMinutes(50))), round.due());
    }

    @Test
    void anInputIsAsItsStripLeavesIt() {
        Goal goal = unmet();
        Button member = click("member");
        Map<String, Input> inputs = new LinkedHashMap<>();
        inputs.put("member", member);
        inputs.put("list", Strip.of(Scroll.wheel(-1), marks("member")).never("spends gems"));
        Map<String, MapScreen> screens = Map.of("HOME", new MapScreen(Setup.named("HOME").start(), inputs, Map.of(),
                Map.of(), Map.of("take", Do.press(member).serves(goal))));

        Round round = Planner.round(new PlanModel(Plan.of(Plan.tier(goal)), ScreenGraph.of(screens), records(), "HOME"));

        assertEquals(new Why.Blocked(Block.NEVER), why(round, "HOME.take"), "its strip's never is its own");
    }

    @Test
    void aFieldOnlyABodyFillsIsNoChoice() {
        Goal goal = unmet();
        on("HOME", "name", Do.press(click("name").typed()).serves(goal));

        assertEquals(new Why.Blocked(Block.NO_INPUT), why(round(Plan.of(Plan.tier(goal))), "HOME.name"));
    }

    @Test
    void aGoalBlockedForNowWaitsAndTheTierBelowActs() {
        Goal energy = unmet();
        Goal gold = unmet();
        on("SHOP", "buyEnergy", press("buyEnergy").serves(energy).needs((Condition) () -> Answer.NO));
        on("HOME", "sell", press("sell").serves(gold));

        Round round = round(Plan.of(Plan.tier(energy), Plan.tier(gold)));

        assertEquals(new Why.Blocked(Block.CONDITION), why(round, "SHOP.buyEnergy"));
        assertEquals(OptionalInt.of(1), round.tier(), "a need saying no for now holds its tier as waiting, not stuck");
        assertEquals(List.of("HOME.sell"), keys(round.route()));
        assertEquals(Optional.of(NOON.plus(Planner.RECHECK)), round.due(), "looked at again in a minute");
    }

    @Test
    void aGoalBlockedForGoodHoldsItsTier() {
        Goal goal = unmet();
        Goal below = unmet();
        on("HOME", "name", Do.press(click("name").typed()).serves(goal));
        on("HOME", "sell", press("sell").serves(below));

        Round round = round(Plan.of(Plan.tier(goal), Plan.tier(below)));

        assertEquals(OptionalInt.of(0), round.tier(), "nothing will ever change for it: its tier is taken, and stuck");
        assertEquals(List.of(), round.route());
        assertEquals(Optional.empty(), round.due());
    }

    @Test
    void goalsThatNeedEachOtherAreALoop() {
        Goal a = unmet();
        Goal b = unmet();
        on("HOME", "x", press("x").serves(a).needs(b));
        on("HOME", "y", press("y").serves(b).needs(a));

        Round round = round(Plan.of(Plan.tier(a)));

        assertEquals(new Why.Loop(a), why(round, "HOME.y"), "said as a loop, not as too deep");
        assertEquals(new Why.CannotMeet(b), why(round, "HOME.x"));
    }

    @Test
    void aLoopOnOnePathIsNoVerdictOnAnother() {
        Goal a = unmet();
        Goal b = unmet();
        Goal c = unmet();
        on("HOME", "x", press("x").serves(a).needs(b));
        on("HOME", "z", press("z").serves(a));
        on("HOME", "y", press("y").serves(b).needs(a));
        on("HOME", "w", press("w").serves(c).needs(b));

        Round round = round(Plan.of(Plan.tier(a), Plan.tier(c)));

        Round below = round(Plan.of(Plan.tier(c)));
        assertEquals(new Why.Needs(b, 2), why(below, "HOME.w"), "b through y, after a through z");
        assertEquals(new Why.Needs(b, 2), why(round, "HOME.w"),
                "the same with a resolved first: b's loop under a holds only there");
    }

    @Test
    void anUnknownUntilIsNoKnownReady() {
        Goal goal = unmet();
        on("HOME", "fight", press("fight").serves(goal).until(() -> Answer.UNKNOWN));

        assertEquals(new Why.Unknown(), why(round(Plan.of(Plan.tier(goal))), "HOME.fight"));
    }

    @Test
    void aSubGoalMayOpenTheWay() {
        Goal level = unmet();
        Goal sword = unmet();
        on("HOME", "train", press("train").serves(level));
        on("SHOP", "buy", press("buy").serves(sword).needs(level));
        Map<String, MapScreen> screens = new LinkedHashMap<>();
        screens.put("HOME", screen(Setup.named("HOME").start(), "HOME", Map.of("shop", click("shop"))));
        screens.put("SHOP", screen(Setup.named("SHOP").from(ref("HOME", "shop")).fromWhen(() -> false), "SHOP", Map.of()));
        PlanModel model = new PlanModel(Plan.of(Plan.tier(sword)), ScreenGraph.of(screens), records(), "HOME");

        assertEquals(List.of("HOME.train", "SHOP.buy"), keys(Planner.round(model).route()));
    }

    @Test
    void aKeyOrALockIsNoWayThere() {
        Goal goal = unmet();
        on("SHOP", "sell", press("sell").serves(goal));
        on("VAULT", "open", press("open").serves(goal));
        Map<String, MapScreen> screens = new LinkedHashMap<>();
        screens.put("HOME", screen(Setup.named("HOME").start(), "HOME",
                Map.of("shop", key(Key.ENTER), "vault", click("vault").locked(click("padlock").picture()))));
        screens.put("SHOP", screen(Setup.named("SHOP").from(ref("HOME", "shop")), "SHOP", Map.of()));
        screens.put("VAULT", screen(Setup.named("VAULT").from(ref("HOME", "vault")), "VAULT", Map.of()));

        Round round = Planner.round(new PlanModel(Plan.of(Plan.tier(goal)), ScreenGraph.of(screens), records(), "HOME"));

        assertEquals(new Why.Blocked(Block.NO_WAY_THERE), why(round, "SHOP.sell"));
        assertEquals(new Why.Blocked(Block.NO_WAY_THERE), why(round, "VAULT.open"));
    }

    @Test
    void aPopupIsCountedFromHome() {
        Goal goal = unmet();
        on("HOME", "work", press("work").serves(goal));
        Map<String, MapScreen> screens = new LinkedHashMap<>();
        screens.put("HOME", screen(Setup.named("HOME").start(), "HOME", Map.of()));
        screens.put("AD", screen(Setup.named("AD").popup(), "AD", Map.of()));
        PlanModel model = new PlanModel(Plan.of(Plan.tier(goal)), ScreenGraph.of(screens), records(), "AD");

        assertEquals("HOME", model.at());
        assertEquals(List.of("HOME.work"), keys(Planner.round(model).route()));
    }

    @Test
    void consideredKeepsThePlansOrder() {
        Goal top = unmet();
        Goal below = unmet();
        on("HOME", "first", press("first").serves(top).needs(below));
        on("HOME", "second", press("second").serves(below));

        Round round = round(Plan.of(Plan.tier(top), Plan.tier(below)));

        assertEquals(List.of("HOME.first", "HOME.second"), round.considered().stream().map(c -> c.step().key()).toList());
    }

    @Test
    void goalsOnTheSameCheckAreOneGoal() {
        Condition never = () -> Answer.NO;
        Goal one = Goal.of(never);
        Goal same = Goal.of(never);
        on("SHOP", "sell", press("sell").serves(one));

        assertEquals(List.of("SHOP.sell"), keys(round(Plan.of(Plan.tier(same))).route()));
    }

    @Test
    void routeStartsWhereTheGameIs() {
        Goal goal = unmet();
        on("HOME", "near", press("near").serves(goal));
        on("VAULT", "far", press("far").serves(goal));

        assertEquals(List.of("VAULT.far"), keys(Planner.round(model(Plan.of(Plan.tier(goal)), "CRYPT")).route()));
        assertEquals(List.of("HOME.near"), keys(Planner.round(model(Plan.of(Plan.tier(goal)), "NOWHERE")).route()),
                "a screen not on the map is taken as home");
    }
}
