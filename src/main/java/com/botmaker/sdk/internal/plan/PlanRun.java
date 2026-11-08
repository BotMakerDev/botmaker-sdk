package com.botmaker.sdk.internal.plan;

import com.botmaker.sdk.api.plan.Goal;
import com.botmaker.sdk.api.plan.Plan;
import com.botmaker.sdk.api.screens.Answer;
import com.botmaker.sdk.api.screens.Do;
import com.botmaker.sdk.api.screens.Pressed;
import com.botmaker.sdk.internal.plan.Round.Considered;
import com.botmaker.sdk.internal.plan.Round.Step;
import com.botmaker.sdk.internal.plan.Round.Why;
import com.botmaker.sdk.internal.screens.ScreenGraph;

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Runs a {@link Plan}: a {@link Planner} round, its first action walked to, looked at again on arrival, pressed and
 * checked, its done-record written; then the next round. Every goal met or waiting, it idles until the soonest is due;
 * none ever due again, it ends. What it does to the game goes through a {@link Game}, a fake in tests.
 */
public final class PlanRun {

    /** How long a run leaves an action be once it found it could not be done on arrival, or its screen not reached. */
    static final Duration HELD_OFF = Duration.ofMinutes(5);

    /** The least time between two presses: a plan that finds the same action again each round presses it no faster. */
    static final Duration PRESS_GAP = Duration.ofSeconds(1);

    /**
     * How many times in a row an action may be pressed, and hold, while no goal it serves comes to be met: past it the
     * press is doing nothing the plan can see, and the action is left be for {@link #HELD_OFF}.
     */
    static final int REPEATS = 3;

    /** Why a run of a plan ended. */
    public enum End {
        /** Every goal is met and none comes due again. */
        MET,
        /** A goal neither met nor waiting has nothing that can be done for it, now or later. */
        STUCK,
        /** An action needs goals nested deeper than {@link Planner#DEEPEST}. */
        TOO_DEEP
    }

    /** What a run of a plan does to the game. */
    public interface Game {

        /** The screen of the map the game shows now; empty when it shows none. */
        Optional<String> where();

        /** Walks to {@code screen}; false when the way there is not open now. A walk that gets stuck throws. */
        boolean walk(String screen);

        /** Presses {@code step}'s input on its screen, which the game shows: what came of it. */
        Pressed.Result press(Step step);

        /** Reads again, off the screen {@code screen} shows now, each of {@code facts} it feeds. */
        void readAgain(String screen, Set<Method> facts);

        /** The screens whose readouts feed {@code fact}; empty when none does. */
        List<String> screensOf(Method fact);

        /** Whether {@code fact} holds a reading that is not stale. */
        boolean fresh(Method fact);

        /** Waits {@code time}; a stop ends the wait by its throw. */
        void idle(Duration time);

        /** Says one decision of the run, carrying {@code values} for its report. */
        void say(String line, Map<String, String> values);
    }

    private final Plan plan;
    private final ScreenGraph map;
    private final DoneRecords records;
    private final Game game;
    private final Function<Goal, String> names;
    /** What was found not doable on arrival, by identity, and until when it is left be. */
    private final Map<Do, Instant> heldOff = new IdentityHashMap<>();
    /** Each fact gone to read, and when: not gone to again before {@link #HELD_OFF} has passed. */
    private final Map<Method, Instant> wentToRead = new HashMap<>();
    /** The action of the last press, when it held and met none of its goals; null otherwise. */
    private Do fruitless;
    /** How many presses in a row, with nothing else pressed between, {@link #fruitless} has had. */
    private int fruitlessTimes;
    /** When the last press that was made ended; null before the first. */
    private Instant lastPress;

    /** A run of {@code plan} over {@code map}, keeping {@code records}, acting through {@code game}; goals by {@code names}. */
    public PlanRun(Plan plan, ScreenGraph map, DoneRecords records, Game game, Function<Goal, String> names) {
        this.plan = plan;
        this.map = map;
        this.records = records;
        this.game = game;
        this.names = names;
    }

    /** Runs rounds until the plan ends, and says why it did. A stop ends it by its throw. */
    public End run() {
        while (true) {
            Optional<End> end = round();
            if (end.isPresent()) return end.get();
        }
    }

    /** One round: something done, gone to, waited for, or the end. */
    Optional<End> round() {
        PlanModel model = model(game.where().orElse(null));
        Round round = Planner.round(model);
        if (round.doing().isEmpty()) return idleOrEnd(model, round);
        Step next = round.doing().getFirst();
        if (goneToRead(model, round, next)) return Optional.empty();
        doStep(model, round, next);
        return Optional.empty();
    }

    private PlanModel model(String at) {
        heldOff.values().removeIf(until -> !until.isAfter(records.clock().instant()));
        return new PlanModel(plan, map, records, at, heldOff);
    }

    /** Nothing to do now: idles until the soonest goal is due, else ends, met or stuck. */
    private Optional<End> idleOrEnd(PlanModel model, Round round) {
        Instant now = records.clock().instant();
        if (round.due().isPresent()) {
            say("Every goal is met or waiting: idle until " + time(round.due().get()) + ".", null, null);
            game.idle(Duration.between(now, round.due().get()));
            return Optional.empty();
        }
        if (round.tier().isEmpty()) {
            say("Every goal of the plan is met, and none comes due again: the plan is done.", null, null);
            return Optional.of(End.MET);
        }
        List<Goal> stuck = plan.tiers().get(round.tier().getAsInt()).goals().stream()
                .filter(goal -> model.answer(goal) != Answer.YES).toList();
        Optional<Considered> deep = tooDeep(round, stuck);
        if (deep.isPresent()) {
            Why.TooDeep why = (Why.TooDeep) deep.get().why();
            say(deep.get().step().key() + " needs " + name(why.goal()) + ", a goal more than " + Planner.DEEPEST
                    + " deep: the plan stops. Have it need a goal nearer the plan.", deep.get().goal(), deep.get().step());
            return Optional.of(End.TOO_DEEP);
        }
        String why = stuck.stream().map(goal -> name(goal) + " (" + whyNot(round, goal) + ")")
                .collect(Collectors.joining(", "));
        say("Nothing can be done for " + why + ", now or later: the plan stops, as the tiers below it wait for it.",
                stuck.isEmpty() ? null : stuck.getFirst(), null);
        return Optional.of(End.STUCK);
    }

    /**
     * The option too deep that leaves {@code stuck} with nothing to do: one of theirs, or of a sub-goal they cannot
     * meet, followed down. Only what stops the tier taken stops the plan; an option too deep elsewhere was passed over.
     */
    private static Optional<Considered> tooDeep(Round round, List<Goal> stuck) {
        Set<Goal> under = new LinkedHashSet<>(stuck);
        boolean grew = true;
        while (grew) {
            grew = false;
            for (Considered c : round.considered()) {
                if (!under.contains(c.goal())) continue;
                if (c.why() instanceof Why.TooDeep) return Optional.of(c);
                if (c.why() instanceof Why.CannotMeet cannot) grew |= under.add(cannot.goal());
            }
        }
        return Optional.empty();
    }

    /** What the actions serving {@code goal} were found to be, in a few words. */
    private String whyNot(Round round, Goal goal) {
        List<String> said = round.considered().stream().filter(c -> c.goal().equals(goal))
                .map(c -> c.step().key() + " " + words(c.why())).toList();
        return said.isEmpty() ? "no action serves it" : String.join("; ", said);
    }

    /**
     * Goes to read an unknown fact first, when a screen whose readout feeds it is on the route or one step off it: one
     * the next action, a goal of its tier or one of its needs reads. False when there is none to go to.
     */
    private boolean goneToRead(PlanModel model, Round round, Step next) {
        Set<Method> unread = new LinkedHashSet<>(FactsRead.of(next.action()));
        if (round.tier().isPresent()) {
            for (Goal goal : plan.tiers().get(round.tier().getAsInt()).goals()) {
                if (model.answer(goal) == Answer.UNKNOWN) unread.addAll(FactsRead.of(goal));
            }
        }
        for (Goal need : next.action().goalNeeds()) {
            if (model.answer(need) == Answer.UNKNOWN) unread.addAll(FactsRead.of(need));
        }
        Instant now = records.clock().instant();
        for (Method fact : unread) {
            if (game.fresh(fact)) continue;
            Instant went = wentToRead.get(fact);
            if (went != null && now.isBefore(went.plus(HELD_OFF))) continue;
            for (String screen : game.screensOf(fact)) {
                // Arriving there reads it anyway, and where the game is was read when it was seen.
                if (screen.equals(next.screen()) || screen.equals(model.at())) break;
                if (!Planner.near(model, round.crossed(), screen)) continue;
                wentToRead.put(fact, now);
                if (!game.walk(screen)) continue;
                say(fact.getName() + " is unknown: read it on " + map.shown(screen) + " first.", null, next);
                return true;
            }
        }
        return false;
    }

    /** Walks to {@code next}, looks again, presses it, checks it and keeps its record. */
    private void doStep(PlanModel model, Round round, Step next) {
        Goal goal = goalOf(round, next);
        say(name(goal) + " → " + next.key() + " (" + reason(round, next) + ")", goal, next);
        boolean mine = records.start(next.action());
        try {
            if (!next.screen().equals(model.at()) && !walk(next)) {
                holdOff(next);
                say(next.key() + ": " + map.shown(next.screen()) + " cannot be reached now; tried again in "
                        + HELD_OFF.toMinutes() + " min.", goal, next);
                return;
            }
            // Before looking again, so what the gap lets change is seen.
            keepGap();
            // Arriving read what its screen shows: the round may choose otherwise now.
            Round again = Planner.round(model(next.screen()));
            if (again.doing().isEmpty() || again.doing().getFirst().action() != next.action()) {
                // Left be, so what came first there is done before the run could come back for this one.
                holdOff(next);
                say(next.key() + " is no more the first thing to do once there: left be for " + HELD_OFF.toMinutes()
                        + " min, choosing again.", goal, next);
                return;
            }
            Pressed.Result result = game.press(next);
            if (result == Pressed.Result.DONE || result == Pressed.Result.LEFT) lastPress = records.clock().instant();
            Set<Method> facts = new LinkedHashSet<>(FactsRead.of(next.action()));
            next.action().serves().forEach(served -> facts.addAll(FactsRead.of(served)));
            String here = game.where().orElse(null);
            if (here != null && !facts.isEmpty()) game.readAgain(here, facts);
            switch (result) {
                case DONE, LEFT -> checked(next, goal, result);
                default -> {
                    fruitless = null;
                    holdOff(next);
                    say(next.key() + " " + notPressed(result) + " on arrival: left be for " + HELD_OFF.toMinutes()
                            + " min, choosing again.", goal, next);
                }
            }
        } finally {
            if (mine) records.finish(next.action());
        }
    }

    /**
     * Walks to {@code step}'s screen: false when the way is not open now. A walk that gets stuck holds the action off
     * before its throw goes on to the run's recovery, so the run does not walk into it again at once.
     */
    private boolean walk(Step step) {
        try {
            return game.walk(step.screen());
        } catch (RuntimeException stuck) {
            holdOff(step);
            throw stuck;
        }
    }

    /** {@code next} pressed: whether it worked, kept in its record as a flow's press is, and said when it did not. */
    private void checked(Step next, Goal goal, Pressed.Result result) {
        Do action = next.action();
        records.pressed(next.key(), action, result == Pressed.Result.DONE);
        if (records.last(next.key()).map(DoneRecords.Done::held).orElse(false)) {
            countFruitless(next);
            return;
        }
        fruitless = null;
        String failed = action.checks() == null ? "the game showed another screen than the map says"
                : "its check said no";
        String again = Readiness.dueAt(action, records.last(next.key()), records.clock())
                .map(at -> "tried again at " + time(at)).orElse("not tried again");
        say(next.key() + " did not work: " + failed + "; " + again + ".", goal, next);
    }

    /**
     * A press that held. When the goals its action serves read no fact (a bot's own method, which the run cannot see
     * move) and none is met after it, one more in a row, with nothing else pressed between; at {@link #REPEATS} the
     * action is left be, so a check that never answers yes cannot press it forever. A goal of facts is not counted:
     * its readings show each press's effect, as a threshold reached a step at a time does.
     */
    private void countFruitless(Step next) {
        Do action = next.action();
        PlanModel after = model(game.where().orElse(null));
        Optional<Goal> blind = action.serves().stream()
                .filter(g -> g instanceof Goal.Of && FactsRead.of(g).isEmpty() && after.answer(g) != Answer.YES)
                .findFirst();
        boolean anyMet = action.serves().stream().anyMatch(g -> after.answer(g) == Answer.YES);
        if (blind.isEmpty() || anyMet) {
            fruitless = null;
            return;
        }
        fruitlessTimes = fruitless == action ? fruitlessTimes + 1 : 1;
        fruitless = action;
        if (fruitlessTimes < REPEATS) return;
        int times = fruitlessTimes;
        fruitless = null;
        holdOff(next);
        say(next.key() + " was pressed " + times + " times in a row and " + name(blind.get()) + " is still not met:"
                + " left be for " + HELD_OFF.toMinutes() + " min.", blind.get(), next);
    }

    /** Waits what is left of {@link #PRESS_GAP} since the last press. */
    private void keepGap() {
        if (lastPress == null) return;
        Duration since = Duration.between(lastPress, records.clock().instant());
        if (since.compareTo(PRESS_GAP) < 0) game.idle(PRESS_GAP.minus(since));
    }

    private void holdOff(Step step) {
        heldOff.put(step.action(), records.clock().instant().plus(HELD_OFF));
    }

    /** The goal {@code step} was chosen for: the first it is a choice of, in the round's order. */
    private static Goal goalOf(Round round, Step step) {
        return round.considered().stream()
                .filter(c -> c.step().action() == step.action() && Round.possible(c.why()))
                .map(Considered::goal).findFirst().orElse(null);
    }

    /** Why {@code step} was taken, in a few words: its bucket, how far its screen is, what it waits for. */
    private String reason(Round round, Step step) {
        Do action = step.action();
        String bucket = action.isFirst() ? "first" : action.isLast() ? "last resort" : "plain";
        int along = Math.max(0, round.crossed().indexOf(step.screen()));
        String screens = along == 0 ? "here" : along == 1 ? "1 screen" : along + " screens";
        String onTheWay = round.onTheWay().stream().anyMatch(s -> s.action() == action) ? ", on the way" : "";
        Optional<Why> why = round.considered().stream().filter(c -> c.step().action() == action).map(Considered::why)
                .filter(Round::possible).findFirst();
        String more = why.filter(w -> !(w instanceof Why.Ready)).map(w -> ", " + words(w)).orElse("");
        return bucket + ", " + screens + onTheWay + more;
    }

    /** {@code why} in a few words, as the Goals view says it. */
    private String words(Why why) {
        return switch (why) {
            case Why.Ready ignored -> "ready";
            case Why.LastResort ignored -> "last resort";
            case Why.Unknown ignored -> "unknown, will look";
            case Why.Needs needs -> "needs " + name(needs.goal()) + " + " + needs.actions()
                    + (needs.actions() == 1 ? " action" : " actions");
            case Why.Waiting waiting -> "waits until " + time(waiting.until());
            case Why.Blocked blocked -> "blocked: " + blocked.block().name().toLowerCase().replace('_', ' ');
            case Why.CannotMeet cannot -> "needs " + name(cannot.goal()) + ", which nothing can meet";
            case Why.TooDeep deep -> "needs " + name(deep.goal()) + ", too deep";
            case Why.Loop loop -> "needs " + name(loop.goal()) + ", which needs it";
        };
    }

    private static String notPressed(Pressed.Result result) {
        return switch (result) {
            case ABSENT -> "was not on its screen";
            case DISABLED -> "showed its disabled look";
            case NOT_NOW -> "could not be pressed now";
            default -> "showed a look that is never pressed";
        };
    }

    private String name(Goal goal) {
        String name = goal == null ? null : names.apply(goal);
        return name == null ? "a goal" : name;
    }

    private String time(Instant at) {
        ZoneId zone = records.clock().getZone();
        return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(zone).format(at);
    }

    /** Says {@code line}, with the goal and the action it is about for the run's report. */
    private void say(String line, Goal goal, Step step) {
        Map<String, String> values = new LinkedHashMap<>();
        if (goal != null) values.put(PlanValues.GOAL, name(goal));
        if (step != null) values.put(PlanValues.ACTION, step.key());
        game.say(line, values);
    }
}
