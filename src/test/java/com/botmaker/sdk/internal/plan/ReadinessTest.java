package com.botmaker.sdk.internal.plan;

import com.botmaker.sdk.api.plan.Goal;
import com.botmaker.sdk.api.screens.Answer;
import com.botmaker.sdk.api.screens.Condition;
import com.botmaker.sdk.api.screens.Do;
import com.botmaker.sdk.api.screens.Retry;
import com.botmaker.sdk.internal.plan.DoneRecords.Done;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static com.botmaker.sdk.internal.screens.MapFixtures.click;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReadinessTest {

    private static final Instant NOON = Instant.parse("2026-11-06T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOON, ZoneOffset.UTC);
    private static final Do CLAIM = Do.press(click("claim"));

    private static Optional<Done> held(Duration ago) {
        return Optional.of(new Done(NOON.minus(ago), true, 0));
    }

    private static Optional<Done> failed(Duration ago, int fails) {
        return Optional.of(new Done(NOON.minus(ago), false, fails));
    }

    private static boolean ready(Do action, Optional<Done> last) {
        return Readiness.ready(action, action.input(), last, CLOCK);
    }

    @Test
    void neverDoneIsDueAndNoCadenceIsAlwaysDue() {
        assertTrue(ready(CLAIM.once(), Optional.empty()));
        assertTrue(ready(CLAIM, held(Duration.ZERO)));
    }

    @Test
    void everyWaitsItsTime() {
        Do hourly = CLAIM.every(Duration.ofHours(1));
        assertFalse(ready(hourly, held(Duration.ofMinutes(59))));
        assertTrue(ready(hourly, held(Duration.ofHours(1))));
    }

    @Test
    void dailyComesRoundAtItsTime() {
        Do morning = CLAIM.daily(LocalTime.of(5, 0));
        assertFalse(ready(morning, held(Duration.ofHours(6))), "done at 06:00, after today's 05:00");
        assertTrue(ready(morning, held(Duration.ofHours(8))), "done at 04:00, before it");

        Do evening = CLAIM.daily(LocalTime.of(20, 0));
        assertFalse(ready(evening, held(Duration.ofHours(1))), "20:00 has not come: yesterday's counts");
        assertTrue(ready(evening, held(Duration.ofHours(17))), "done at 19:00 yesterday, before yesterday's 20:00");
    }

    @Test
    void onceIsDoneOnceItsCheckHeld() {
        assertFalse(ready(CLAIM.once(), held(Duration.ofDays(400))));
        assertTrue(ready(CLAIM.once().onFail(Retry.again(1)), failed(Duration.ZERO, 1)), "it has not worked yet");
    }

    @Test
    void aFailureFollowsItsRetry() {
        assertFalse(ready(CLAIM, failed(Duration.ofMinutes(29), 1)), "half an hour by default");
        assertTrue(ready(CLAIM, failed(Duration.ofMinutes(30), 1)));
        assertFalse(ready(CLAIM.onFail(Retry.never()), failed(Duration.ofDays(400), 1)));

        Do twice = CLAIM.onFail(Retry.again(2));
        assertTrue(ready(twice, failed(Duration.ZERO, 2)));
        assertFalse(ready(twice, failed(Duration.ofDays(400), 3)), "tries spent, and no cadence to bring it back");
        Do hourly = twice.every(Duration.ofHours(1));
        assertFalse(ready(hourly, failed(Duration.ofMinutes(1), 3)));
        assertTrue(ready(hourly, failed(Duration.ofHours(1), 3)), "its cadence brings it back");
    }

    @Test
    void dueAtSaysWhen() {
        assertEquals(Optional.of(NOON), Readiness.dueAt(CLAIM.once(), Optional.empty(), CLOCK), "never done: now");
        assertEquals(Optional.of(NOON.plus(Duration.ofMinutes(50))),
                Readiness.dueAt(CLAIM.every(Duration.ofHours(1)), held(Duration.ofMinutes(10)), CLOCK));
        assertEquals(Optional.of(Instant.parse("2026-11-07T05:00:00Z")),
                Readiness.dueAt(CLAIM.daily(LocalTime.of(5, 0)), held(Duration.ofHours(6)), CLOCK), "done at 06:00");
        assertEquals(Optional.of(Instant.parse("2026-11-06T05:00:00Z")),
                Readiness.dueAt(CLAIM.daily(LocalTime.of(5, 0)), held(Duration.ofHours(8)), CLOCK), "done at 04:00");
        assertEquals(Optional.of(Instant.parse("2026-11-07T05:00:00Z")),
                Readiness.dueAt(CLAIM.daily(LocalTime.of(5, 0)), held(Duration.ofHours(7)), CLOCK), "done at 05:00 sharp");
        assertEquals(Optional.of(NOON.plus(Duration.ofMinutes(20))),
                Readiness.dueAt(CLAIM, failed(Duration.ofMinutes(10), 1), CLOCK), "half an hour after it failed");
        assertEquals(Optional.of(NOON), Readiness.dueAt(CLAIM, held(Duration.ofDays(-1)), CLOCK),
                "no cadence: now, even for a record from a clock ahead");
        assertTrue(ready(CLAIM.onFail(Retry.again(2)), failed(Duration.ofDays(-1), 1)), "tries left, the same");
        assertEquals(Optional.empty(), Readiness.dueAt(CLAIM.once(), held(Duration.ofDays(1)), CLOCK));
        assertEquals(Optional.empty(), Readiness.dueAt(CLAIM.onFail(Retry.never()), failed(Duration.ofDays(1), 1), CLOCK));
    }

    @Test
    void onlyANoHoldsItBack() {
        assertFalse(ready(CLAIM.needs(Goal.of((Condition) () -> Answer.NO)), Optional.empty()));
        assertTrue(ready(CLAIM.needs(Goal.of((Condition) () -> Answer.UNKNOWN)), Optional.empty()));
        assertFalse(ready(CLAIM.needs(() -> Answer.NO), Optional.empty()));
        assertTrue(ready(CLAIM.needs(() -> Answer.UNKNOWN), Optional.empty()));
        assertFalse(ready(CLAIM.until(() -> Answer.YES), Optional.empty()));
        assertTrue(ready(CLAIM.until(() -> Answer.UNKNOWN), Optional.empty()));
        assertFalse(ready(Do.press(click("claim").never("spends gems")), Optional.empty()));
        assertFalse(ready(Do.press(click("claim").when(() -> false)), Optional.empty()));
        assertFalse(ready(Do.press(click("claim").locked(click("padlock").picture())), Optional.empty()));
    }
}
