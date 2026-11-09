package com.botmaker.sdk.api.screens;

import com.botmaker.plugin.api.value.Link;
import com.botmaker.sdk.api.plan.Goal;
import com.botmaker.sdk.api.plan.Reset;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * What achieves something on a {@link Screen}: claim, buy, upgrade, fight. A {@code public final} field of its screen
 * beside the input it presses; an input that only leads to another screen needs none, the map walks it.
 *
 * <pre>{@code
 * public final Do claimChest = Do.press(chest).serves(Goals.ENOUGH_GOLD).first().daily(Reset.daily(LocalTime.of(5, 0)));
 * public final Do sellLoot   = Do.press(sellLootButton).serves(Goals.ENOUGH_GOLD).over(() -> Screens.ARENA.fight);
 * public final Do closeAd    = Do.press(adClose).onSight();
 * }</pre>
 *
 * <p>It is one input, whose gesture is the input's own; what is no press is a {@link Custom} input. It is no
 * {@link ScreenPart}: what a screen shows is what the map tells it by, what is done there says nothing of that.
 * {@link Navigate#press(Do)} presses one from a flow and keeps its done-record.
 *
 * <p>Studio reads and writes it as {@code Do.press(…)} and its links ({@code PlanTypes.DO_SHAPE}): the two
 * {@code needs} and the conditions {@code until} and {@code checks} are declared there, since a condition crosses as
 * its method reference's text.
 *
 * @param input          what it presses
 * @param serves         the goals it is meant to meet
 * @param goalNeeds      the goals that must be met first
 * @param conditionNeeds what must hold first, and only blocks: a guard such as "never spend gems"
 * @param isFirst        whether it is preferred among the actions serving a goal
 * @param isLast         whether it is the last resort among them
 * @param over           the actions it is tried before, for a goal both serve; named lazily, as screens name each other
 * @param isOnSight      whether it is done whenever its screen shows and it is ready, whatever the goals
 * @param every          how long after it was done it may be done again; null when no such time holds it back
 * @param daily          the game's reset it may be done again from; null when no reset holds it back
 * @param isOnce         whether it is done once for good, once its check held
 * @param until          what ends it: not done once this says yes; null when nothing does
 * @param checks         how a run knows it worked; null when only its press tells
 * @param onFail         what it does once its check says no
 */
public record Do(Input input, List<Goal> serves, List<Goal> goalNeeds, List<Condition> conditionNeeds, boolean isFirst,
                 boolean isLast, List<Supplier<Do>> over, boolean isOnSight, Duration every, Reset daily,
                 boolean isOnce, Condition until, Condition checks, Retry onFail) {

    public Do {
        Objects.requireNonNull(input, "A Do presses an input of its screen, declared above it.");
        serves = List.copyOf(serves);
        goalNeeds = List.copyOf(goalNeeds);
        conditionNeeds = List.copyOf(conditionNeeds);
        over = List.copyOf(over);
        onFail = onFail == null ? Retry.DEFAULT : onFail;
    }

    /** The action of pressing {@code input}, a field of its screen declared above it. */
    public static Do press(Input input) {
        return new Do(input, List.of(), List.of(), List.of(), false, false, List.of(), false, null, null, false, null,
                null, null);
    }

    /** These goals too are what it is meant to meet; the planner chains only through what an action serves. */
    @Link
    public Do serves(Goal... goals) {
        return new Do(input, plus(serves, goals), goalNeeds, conditionNeeds, isFirst, isLast, over, isOnSight, every,
                daily, isOnce, until, checks, onFail);
    }

    /** These goals must be met first: an unmet one becomes a sub-goal. */
    public Do needs(Goal... goals) {
        return new Do(input, serves, plus(goalNeeds, goals), conditionNeeds, isFirst, isLast, over, isOnSight, every,
                daily, isOnce, until, checks, onFail);
    }

    /** These must hold first: one that says no only blocks. */
    public Do needs(Condition... conditions) {
        return new Do(input, serves, goalNeeds, plus(conditionNeeds, conditions), isFirst, isLast, over, isOnSight,
                every, daily, isOnce, until, checks, onFail);
    }

    /** Preferred among the actions serving the same goal; no more a {@link #last}. */
    @Link
    public Do first() {
        return new Do(input, serves, goalNeeds, conditionNeeds, true, false, over, isOnSight, every, daily, isOnce,
                until, checks, onFail);
    }

    /** The last resort among the actions serving the same goal; no more a {@link #first}. */
    @Link
    public Do last() {
        return new Do(input, serves, goalNeeds, conditionNeeds, false, true, over, isOnSight, every, daily, isOnce,
                until, checks, onFail);
    }

    /** Tried before these, for a goal both serve: {@code .over(() -> Screens.ARENA.fight)}. */
    @Link
    @SafeVarargs
    public final Do over(Supplier<Do>... others) {
        return new Do(input, serves, goalNeeds, conditionNeeds, isFirst, isLast, plus(over, others), isOnSight, every,
                daily, isOnce, until, checks, onFail);
    }

    /**
     * Done whenever a run's walk comes to its screen, it shows and it is ready, whatever the goals: an ad closed, a
     * free reward taken. A {@code .popup()} screen closes by its own way out, and no action on it is done on sight.
     */
    @Link
    public Do onSight() {
        return new Do(input, serves, goalNeeds, conditionNeeds, isFirst, isLast, over, true, every, daily, isOnce,
                until, checks, onFail);
    }

    /** Done again only {@code time} after it was last done, in place of any {@link #daily} or {@link #once}. */
    @Link
    public Do every(Duration time) {
        Objects.requireNonNull(time, "time");
        if (time.isNegative() || time.isZero()) {
            throw new IllegalArgumentException("Do.every takes a time ahead, not " + time + ".");
        }
        return new Do(input, serves, goalNeeds, conditionNeeds, isFirst, isLast, over, isOnSight, time, null, false,
                until, checks, onFail);
    }

    /**
     * Done again only once the game's {@code reset} has come since, in place of any {@link #every} or {@link #once}:
     * {@code .daily(Reset.daily(LocalTime.of(5, 0)))}.
     */
    @Link
    public Do daily(Reset reset) {
        return new Do(input, serves, goalNeeds, conditionNeeds, isFirst, isLast, over, isOnSight, null,
                Objects.requireNonNull(reset, "reset"), false, until, checks, onFail);
    }

    /** Done once for good, in place of any {@link #every} or {@link #daily}. */
    @Link
    public Do once() {
        return new Do(input, serves, goalNeeds, conditionNeeds, isFirst, isLast, over, isOnSight, null, null, true,
                until, checks, onFail);
    }

    /** Not done once {@code condition} says yes: "fight until the enemy falls". */
    public Do until(Condition condition) {
        return new Do(input, serves, goalNeeds, conditionNeeds, isFirst, isLast, over, isOnSight, every, daily, isOnce,
                condition, checks, onFail);
    }

    /** How a run knows it worked: it held unless {@code condition} says no. */
    public Do checks(Condition condition) {
        return new Do(input, serves, goalNeeds, conditionNeeds, isFirst, isLast, over, isOnSight, every, daily, isOnce,
                until, condition, onFail);
    }

    /** What it does once its check says no, in place of {@link Retry#DEFAULT}. */
    @Link
    public Do onFail(Retry retry) {
        return new Do(input, serves, goalNeeds, conditionNeeds, isFirst, isLast, over, isOnSight, every, daily, isOnce,
                until, checks, retry);
    }

    private static <T> List<T> plus(List<T> had, T[] more) {
        List<T> all = new ArrayList<>(had);
        all.addAll(Arrays.asList(more));
        return all;
    }
}
