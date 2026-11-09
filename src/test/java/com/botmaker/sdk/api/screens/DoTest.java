package com.botmaker.sdk.api.screens;

import com.botmaker.sdk.api.plan.Goal;
import com.botmaker.sdk.api.plan.Reset;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static com.botmaker.sdk.internal.screens.MapFixtures.click;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DoTest {

    private static final Goal RICH = Goal.of((Condition) () -> Answer.YES);
    private static final Goal STRONG = Goal.of((Condition) () -> Answer.NO);

    @Test
    void traitsChain() {
        Button chest = click("chest");
        Condition guard = () -> Answer.YES;
        Condition worked = () -> Answer.NO;
        Condition ended = () -> Answer.UNKNOWN;

        Do plain = Do.press(chest);
        assertSame(chest, plain.input());
        assertEquals(Retry.later(Duration.ofMinutes(30)), plain.onFail(), "half an hour unless it says");
        assertFalse(plain.isOnSight());

        Do action = plain.serves(RICH).serves(STRONG).needs(RICH).needs(guard).first().onSight()
                .until(ended).checks(worked).onFail(Retry.again(2));
        assertEquals(List.of(RICH, STRONG), action.serves(), "a second serves adds");
        assertEquals(List.of(RICH), action.goalNeeds());
        assertEquals(List.of(guard), action.conditionNeeds());
        assertTrue(action.isFirst());
        assertTrue(action.isOnSight());
        assertSame(ended, action.until());
        assertSame(worked, action.checks());
        assertEquals(new Retry.Again(2), action.onFail());

        assertFalse(action.last().isFirst(), "one bucket at a time");
        assertTrue(action.last().isLast());
        assertSame(chest, action.input(), "a trait keeps the input");
        assertFalse(plain.isFirst(), "a trait leaves the one it came from");
    }

    @Test
    void oneCadenceAtATime() {
        Do every = Do.press(click("chest")).every(Duration.ofHours(1));
        Reset five = Reset.daily(LocalTime.of(5, 0));
        assertEquals(five, every.daily(five).daily());
        assertNull(every.daily(five).every(), "the last one said is the one");
        assertTrue(every.once().isOnce());
        assertNull(every.once().every());
        assertFalse(every.once().every(Duration.ofHours(1)).isOnce());
    }

    @Test
    void itsTimeIsKeptByEveryLinkAndBounded() {
        Do fight = Do.press(click("fight")).takes(Duration.ofSeconds(40));
        assertEquals(Duration.ofSeconds(40), fight.first().every(Duration.ofHours(1)).onFail(Retry.never()).takes(),
                "each link keeps it");
        assertNull(Do.press(click("fight")).takes(), "none unless it says");
        assertThrows(IllegalArgumentException.class, () -> fight.takes(Duration.ofSeconds(-1)));
        assertThrows(IllegalArgumentException.class, () -> fight.takes(Duration.ofDays(2)), "past a day is a typo");
    }

    @Test
    void overTakesSuppliers() {
        AtomicInteger asked = new AtomicInteger();
        Do fight = Do.press(click("fight"));
        Supplier<Do> later = () -> {
            asked.incrementAndGet();
            return fight;
        };

        Do sell = Do.press(click("sell")).over(later);

        assertEquals(0, asked.get(), "named lazily: a screen may not be built yet");
        assertSame(fight, sell.over().getFirst().get());
    }

    @Test
    void refusesWhatMeansNothing() {
        assertTrue(assertThrows(NullPointerException.class, () -> Do.press(null)).getMessage().contains("above it"),
                "an input declared below its Do is still null");
        assertThrows(IllegalArgumentException.class, () -> Do.press(click("a")).every(Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> Retry.again(0));
        assertThrows(IllegalArgumentException.class, () -> Retry.later(Duration.ofSeconds(-1)));
    }
}
