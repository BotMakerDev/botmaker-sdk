package com.botmaker.sdk.internal.plan;

import com.botmaker.sdk.api.bot.BotSettings;
import com.botmaker.sdk.api.plan.Goal;
import com.botmaker.sdk.api.plan.Plan;
import com.botmaker.sdk.api.screens.Answer;
import com.botmaker.sdk.api.screens.Do;
import com.botmaker.sdk.api.screens.Pressed;
import com.botmaker.sdk.internal.bot.BotStuckException;
import com.botmaker.sdk.internal.plan.Round.Considered;
import com.botmaker.sdk.internal.plan.Round.Step;
import com.botmaker.sdk.internal.plan.Round.Why;
import com.botmaker.sdk.internal.screens.ScreenGraph;
import com.botmaker.sdk.internal.trace.Say;

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
 * checked once what its check reads was read again, its done-record written; then the next round. Every goal met or
 * waiting, it idles toward the soonest due time, looking at the game again on the way; none ever due again, it ends. What it does to the game goes through a {@link Game}, a fake in tests.
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

    /**
     * The longest an idle waits before it looks at the game again: the facts its screen feeds read afresh and what it
     * does on sight done, so a goal that falls back unmet is seen before the next one comes due.
     */
    static final Duration IDLE_LOOK = Duration.ofSeconds(30);

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

        /** When {@code fact}'s reading was taken; empty when it holds none. */
        Optional<Instant> readAt(Method fact);

        /** Waits {@code time}; a stop ends the wait by its throw. */
        void idle(Duration time);

        /**
         * Looks at the game again: every fact the screen it shows feeds read afresh, what it does on sight done. True
         * when it pressed anything.
         */
        boolean look();

        /** Says one decision of the run, carrying {@code values} for its report. */
        void say(String line, Map<String, String> values);

        /** Throws the run's stuck exception when Android owes a restart of the game or the game is frozen. */
        void checkpoint();
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
    /** The last press, when its check reads facts not read again since it: proven or let go by the next round. */
    private Unproven unproven;
    /** The due time the run last said it idles until; null when it is not idling. */
    private Instant idling;
    /** The action last put after another on arrival, with nothing pressed since; null otherwise. */
    private Do deferred;
    /** How long the run may act with no progress before it is stuck. */
    private final Duration stuckAfter;
    /**
     * When the run last made progress — a new screen reached, a goal came to be met, a press proven done — moved on
     * by every idle since; null before this call of {@link #run}'s first round.
     */
    private Instant progressed;
    /** The screens rounds started on since the last progress. */
    private final Set<String> screensSince = new LinkedHashSet<>();
    /** The plan's goals met at a round's start since the last progress. */
    private final Set<Goal> metSince = new LinkedHashSet<>();

    /** The goals counted as met by an action that held, before anything read says so ({@link PlanModel#assumedMet}). */
    private final Map<Goal, Assumed> assumed = new HashMap<>();

    /**
     * A goal counted as met: until one of {@code facts} is read at or after {@code settled}, the press plus what its
     * action {@link Do#takes} (a reading taken mid-fight shows the game before the fight's effect), or, reading none,
     * until {@code until}, when the action that served it comes due again.
     */
    private record Assumed(Instant settled, Set<Method> facts, Instant until) {}

    /** A press of {@code step} for {@code goal} at {@code pressed}, which came to {@code result}, its check not read since. */
    private record Unproven(Step step, Goal goal, Instant pressed, Pressed.Result result) {}

    /**
     * A run of {@code plan} over {@code map}, keeping {@code records}, acting through {@code game}; goals by
     * {@code names}; stuck after acting {@code stuckAfter} with no progress.
     */
    public PlanRun(Plan plan, ScreenGraph map, DoneRecords records, Game game, Function<Goal, String> names,
                   Duration stuckAfter) {
        this.plan = plan;
        this.map = map;
        this.records = records;
        this.game = game;
        this.names = names;
        this.stuckAfter = stuckAfter;
    }

    /** A run stuck after {@link BotSettings#DEFAULT_STUCK_AFTER}. */
    PlanRun(Plan plan, ScreenGraph map, DoneRecords records, Game game, Function<Goal, String> names) {
        this(plan, map, records, game, names, BotSettings.DEFAULT_STUCK_AFTER);
    }

    /**
     * Runs rounds until the plan ends, and says why it did. A stop ends it by its throw, and so does a stuck run,
     * which the run's recovery catches before it calls this again: the progress clock starts over then.
     */
    public End run() {
        progressed = null;
        while (true) {
            Optional<End> end = round();
            if (end.isPresent()) return end.get();
        }
    }

    /**
     * One round: something done, gone to, waited for, or the end. A round that acted ends at the watchdog's
     * {@link #checkpoint}.
     */
    Optional<End> round() {
        if (progressed == null) progressed();
        prove();
        String at = game.where().orElse(null);
        PlanModel model = model(at);
        progressSeen(model, at);
        Round round = Planner.round(model);
        if (round.doing().isEmpty()) {
            Instant from = records.clock().instant();
            Optional<End> end = idleOrEnd(model, round);
            // Waiting toward a due time is not acting: the clock leaves it out, and goes on where it was.
            progressed = progressed.plus(Duration.between(from, records.clock().instant()));
            if (end.isEmpty()) game.checkpoint();
            return end;
        }
        idling = null;
        Step next = round.doing().getFirst();
        if (!goneToRead(model, round, next)) doStep(model, round, next);
        checkpoint(round, next);
        return Optional.empty();
    }

    /**
     * Counts reaching a screen, or meeting a goal of the plan, as progress when it was not so at any round since the
     * last progress: a run that goes back and forth between two screens, or a goal that falls unmet and comes back,
     * makes none.
     */
    private void progressSeen(PlanModel model, String at) {
        Set<Goal> nowMet = new LinkedHashSet<>();
        // What the game says: a goal only assumed met after a press is no progress.
        plan.tiers().forEach(tier -> tier.goals().stream().filter(goal -> model.read(goal) == Answer.YES)
                .forEach(nowMet::add));
        boolean moved = at != null && !screensSince.contains(at);
        boolean cameMet = !metSince.containsAll(nowMet);
        if (moved || cameMet) progressed();
        if (at != null) screensSince.add(at);
        metSince.addAll(nowMet);
    }

    /** The run made progress now: the clock starts over, and so does what counts as new. */
    private void progressed() {
        progressed = records.clock().instant();
        screensSince.clear();
        metSince.clear();
    }

    /**
     * The watchdog's look once a round that acted: a restart Android's dialogs owe or a frozen game throws to the
     * run's recovery; so does acting {@link #stuckAfter} with no progress, after {@code next} is held off so the
     * recovered run does not go straight back to it.
     */
    private void checkpoint(Round round, Step next) {
        game.checkpoint();
        Instant now = records.clock().instant();
        if (Duration.between(progressed, now).compareTo(stuckAfter) < 0) return;
        holdOff(next);
        String why = "No progress for " + stuckAfter.toMinutes() + " min: no screen changed, no goal came to be met"
                + " and no press was proven done. " + next.key() + " is left be for " + HELD_OFF.toMinutes()
                + " min; the run recovers: home on the map, else a restart of the game.";
        say(why, goalOf(round, next), next);
        throw Say.told(new BotStuckException(why));
    }

    private PlanModel model(String at) {
        Instant now = records.clock().instant();
        heldOff.values().removeIf(until -> !until.isAfter(now));
        assumed.values().removeIf(held -> held.facts().isEmpty() ? !held.until().isAfter(now)
                : held.facts().stream().anyMatch(fact -> game.readAt(fact).filter(read -> !read.isBefore(held.settled()))
                        .isPresent()));
        return new PlanModel(plan, map, records, at, heldOff, assumed.keySet());
    }

    /**
     * The light effects of {@code step}, pressed at {@code pressed} and held: each goal of facts it serves counts as
     * met until one of them is read once the action's {@link Do#takes} has passed, and each that reads none until the
     * action comes due again. A done-goal reads the record just kept; an action due again at once, or never (done for
     * good), leaves a goal of no fact as its check says.
     */
    private void assume(Step step, Instant pressed) {
        Instant now = records.clock().instant();
        Optional<Instant> due = Readiness.dueAt(step.action(), records.last(step.key()), records.clock());
        Instant settled = pressed.plusMillis(Planner.takes(step.action()));
        for (Goal goal : step.action().serves()) {
            if (!(goal instanceof Goal.Of)) continue;
            Set<Method> facts = FactsRead.of(goal);
            if (facts.isEmpty() && due.filter(until -> until.isAfter(now)).isEmpty()) continue;
            assumed.put(goal, new Assumed(settled, facts, facts.isEmpty() ? due.get() : null));
        }
    }

    /**
     * Nothing to do now: idles toward the soonest goal's due time, {@link #IDLE_LOOK} at most, and looks at the game
     * again, so the next round plans from what it shows; with nothing ever due, ends, met or stuck.
     */
    private Optional<End> idleOrEnd(PlanModel model, Round round) {
        Instant now = records.clock().instant();
        if (round.due().isPresent()) {
            Instant due = round.due().get();
            if (!due.equals(idling)) say("Every goal is met or waiting: idle until " + time(due) + ".", null, null);
            idling = due;
            Duration left = Duration.between(now, due);
            game.idle(left.compareTo(IDLE_LOOK) < 0 ? left : IDLE_LOOK);
            if (game.look()) lastPress = records.clock().instant();
            return Optional.empty();
        }
        idling = null;
        if (round.tier().isEmpty()) {
            // A plan is done on what the game says, never on what a press was assumed to do: plan again from that.
            List<Goal> doubted = assumed.keySet().stream().filter(goal -> model.read(goal) != Answer.YES).toList();
            assumed.clear();
            if (!doubted.isEmpty()) {
                say("Every goal is met or waiting, " + doubted.stream().map(this::name).collect(Collectors.joining(", "))
                        + " only as assumed after a press: planned again from what the game says.", null, null);
                return Optional.empty();
            }
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
            boolean still = again.doing().stream().anyMatch(step -> step.action() == next.action());
            if (still && again.doing().getFirst().action() != next.action() && deferred != next.action()) {
                // Something on the way goes first; this one stays a choice, so the next rounds come back for it. Once
                // only before something is pressed: a second time, the way there and back goes round, and it is left be.
                deferred = next.action();
                say(again.doing().getFirst().key() + " goes first once there; " + next.key() + " after it.", goal, next);
                return;
            }
            deferred = null;
            if (!still) {
                holdOff(next);
                say(next.key() + " is no more a thing to do once there: left be for " + HELD_OFF.toMinutes()
                        + " min, choosing again.", goal, next);
                return;
            }
            Instant pressed = records.clock().instant();
            Pressed.Result result = game.press(next);
            if (result == Pressed.Result.DONE || result == Pressed.Result.LEFT) lastPress = records.clock().instant();
            Set<Method> facts = new LinkedHashSet<>(FactsRead.of(next.action()));
            next.action().serves().forEach(served -> facts.addAll(FactsRead.of(served)));
            String here = game.where().orElse(null);
            if (here != null && !facts.isEmpty()) game.readAgain(here, facts);
            switch (result) {
                case DONE, LEFT -> checked(next, goal, result, pressed);
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

    /**
     * {@code next} pressed at {@code pressed}: kept as {@link #recorded} once every fact its check reads was read since,
     * else left unproven for the next round to read, and counted as a press that showed nothing.
     */
    private void checked(Step next, Goal goal, Pressed.Result result, Instant pressed) {
        Set<Method> unread = unread(next.action(), pressed);
        if (unread.isEmpty()) {
            recorded(next, goal, result, pressed);
            return;
        }
        unproven = new Unproven(next, goal, pressed, result);
        say(next.key() + " pressed, unproven: its check reads " + names(unread) + ", not read since; read again"
                + " next round.", goal, next);
    }

    /** The facts {@code action}'s check reads whose reading was not taken at or after {@code since}. */
    private Set<Method> unread(Do action, Instant since) {
        Set<Method> unread = new LinkedHashSet<>();
        for (Method fact : FactsRead.of(action.checks())) {
            if (game.readAt(fact).filter(at -> !at.isBefore(since)).isEmpty()) unread.add(fact);
        }
        return unread;
    }

    /**
     * The last press left unproven: each fact its check reads and nothing read since is read on a screen that feeds
     * it, walked to when the game is elsewhere. Every one read, the press is {@link #recorded} at the time it was
     * made; else no record is kept, its goal is planned from what is known, and the press counts toward
     * {@link #REPEATS} as one that showed nothing. A walk that gets stuck holds the action off before its throw goes
     * on, so the next press of it does not send the run on the same walk.
     */
    private void prove() {
        Unproven pending = unproven;
        unproven = null;
        if (pending == null) return;
        Step step = pending.step();
        Set<Method> unread = unread(step.action(), pending.pressed());
        String at = game.where().orElse(null);
        for (Method fact : List.copyOf(unread)) {
            if (!unread.contains(fact)) continue;
            for (String screen : game.screensOf(fact)) {
                if (!screen.equals(at)) {
                    try {
                        if (!game.walk(screen)) continue;
                    } catch (RuntimeException stuck) {
                        holdOff(step);
                        throw stuck;
                    }
                    at = screen;
                }
                game.readAgain(screen, unread);
                unread = unread(step.action(), pending.pressed());
                break;
            }
        }
        if (unread.isEmpty()) {
            boolean mine = records.start(step.action());
            try {
                recorded(step, pending.goal(), pending.result(), pending.pressed());
            } finally {
                if (mine) records.finish(step.action());
            }
            return;
        }
        say(step.key() + "'s check reads " + names(unread) + ", which could not be read again since its press: no"
                + " record kept.", pending.goal(), step);
        strike(step, pending.goal(), " and its check could not be read after any");
    }

    private static String names(Set<Method> facts) {
        return facts.stream().map(Method::getName).collect(Collectors.joining(", "));
    }

    /**
     * {@code next} pressed at {@code pressed} and its check read: whether it worked, kept in its record at that time,
     * and said when it did not.
     */
    private void recorded(Step next, Goal goal, Pressed.Result result, Instant pressed) {
        Do action = next.action();
        records.pressed(next.key(), action, result == Pressed.Result.DONE, pressed);
        if (records.last(next.key()).map(DoneRecords.Done::held).orElse(false)) {
            if (result == Pressed.Result.DONE) progressed();
            countFruitless(next);
            assume(next, pressed);
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
                .filter(g -> g instanceof Goal.Of && FactsRead.of(g).isEmpty() && after.read(g) != Answer.YES)
                .findFirst();
        // What the game says, never what an earlier press's light effect assumes.
        boolean anyMet = action.serves().stream().anyMatch(g -> after.read(g) == Answer.YES);
        if (blind.isEmpty() || anyMet) {
            fruitless = null;
            return;
        }
        strike(next, blind.get(), " and " + name(blind.get()) + " is still not met");
    }

    /**
     * One more press of {@code next} in a row that showed nothing; at {@link #REPEATS} it is left be for
     * {@link #HELD_OFF}, said with {@code what} the presses did not show.
     */
    private void strike(Step next, Goal goal, String what) {
        Do action = next.action();
        fruitlessTimes = fruitless == action ? fruitlessTimes + 1 : 1;
        fruitless = action;
        if (fruitlessTimes < REPEATS) return;
        int times = fruitlessTimes;
        fruitless = null;
        holdOff(next);
        say(next.key() + " was pressed " + times + " times in a row" + what + ": left be for " + HELD_OFF.toMinutes()
                + " min.", goal, next);
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
