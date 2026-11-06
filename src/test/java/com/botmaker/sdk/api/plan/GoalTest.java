package com.botmaker.sdk.api.plan;

import com.botmaker.sdk.api.screens.Answer;
import com.botmaker.sdk.api.screens.Button;
import com.botmaker.sdk.api.screens.Condition;
import com.botmaker.sdk.api.screens.Do;
import com.botmaker.sdk.api.screens.Screen;
import com.botmaker.sdk.api.screens.Setup;
import com.botmaker.sdk.internal.plan.DoneRecords;
import com.botmaker.sdk.internal.plan.Readiness;
import com.botmaker.sdk.internal.screens.MapFixtures;
import com.botmaker.sdk.internal.screens.ScreenGraph;
import com.botmaker.sdk.internal.screens.ScreenMaps;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GoalTest {

    private static final Instant NOON = Instant.parse("2026-11-06T12:00:00Z");
    private static final String KEY = DoneRecords.key("HOME", "claim");

    @TempDir
    Path folder;

    private static boolean yes() {
        return true;
    }

    private static boolean no() {
        return false;
    }

    private static boolean boom() {
        throw new IllegalStateException("cannot tell");
    }

    public static final class Home extends Screen {
        public final Button chest = MapFixtures.click("chest");
        public final Do claim = Do.press(chest);

        @Override
        public Setup setup() {
            return Setup.named("Home");
        }
    }

    private static final Home HOME = new Home();
    private static final ScreenGraph MAP = ScreenGraph.ofScreens(Map.of("HOME", HOME));

    private DoneRecords at(Instant now) {
        return new DoneRecords(folder.resolve("done.json"), Clock.fixed(now, ZoneOffset.UTC));
    }

    private Answer met(Goal.Done goal, Duration after) {
        return Readiness.met(goal, MAP, at(NOON.plus(after)));
    }

    @AfterEach
    void noMapLeft() {
        ScreenMaps.use(null);
    }

    @Test
    void supplierThrowIsUnknown() {
        assertEquals(Answer.YES, Goal.of(GoalTest::yes).answer());
        assertEquals(Answer.NO, Goal.of(GoalTest::no).answer());
        assertEquals(Answer.UNKNOWN, Goal.of(GoalTest::boom).answer());
        Condition unknown = () -> Answer.UNKNOWN;
        assertEquals(Answer.UNKNOWN, Goal.of(unknown).answer(), "a Condition keeps its three answers");
        assertSame(unknown, ((Goal.Of) Goal.of(unknown)).check());
        assertEquals(Answer.YES, Goal.of(GoalTest::overloaded).answer(), "an overloaded method can be named");
    }

    private static boolean overloaded() {
        return true;
    }

    @SuppressWarnings("unused")
    private static boolean overloaded(int times) {
        return false;
    }

    @Test
    void doneWithinReadsTheRecord() {
        Goal.Done goal = Goal.done(() -> HOME.claim).within(Duration.ofDays(1));
        assertEquals(Answer.NO, met(goal, Duration.ZERO), "never done");

        at(NOON).done(KEY, true);

        assertEquals(Answer.YES, met(goal, Duration.ofHours(23)));
        assertEquals(Answer.NO, met(goal, Duration.ofDays(1)), "when an every(1 day) is due again, the goal is unmet");
        assertEquals(Answer.NO, met(goal, Duration.ofDays(2)));
        assertEquals(Answer.YES, met(Goal.done(() -> HOME.claim), Duration.ofDays(400)), "no time: once is enough");
    }

    @Test
    void aFailedCheckDoesNotMeetIt() {
        at(NOON).done(KEY, true);
        at(NOON).done(KEY, false);

        assertEquals(Answer.NO, met(Goal.done(() -> HOME.claim), Duration.ZERO));
    }

    @Test
    void anActionNoScreenHoldsIsUnknown() {
        ScreenMaps.use(ScreenGraph.of(Map.of()));

        assertEquals(Answer.UNKNOWN, Goal.done(() -> HOME.claim).answer(), "not in the map");
        assertEquals(Answer.UNKNOWN, Goal.done(() -> Do.press(MapFixtures.click("other"))).answer(), "another action");
        assertEquals(Answer.UNKNOWN, Goal.done(() -> null).answer(), "not there yet");
        assertEquals(Answer.UNKNOWN, Goal.done(() -> {
            throw new IllegalStateException("cannot tell");
        }).answer());
    }

    @Test
    void refusesWhatMeansNothing() {
        assertThrows(NullPointerException.class, () -> Goal.done(null));
        assertThrows(IllegalArgumentException.class, () -> Goal.done(() -> HOME.claim).within(Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> Goal.done(() -> HOME.claim).within(Duration.ofDays(-1)));
        assertThrows(NullPointerException.class, () -> Goal.done(() -> HOME.claim).within(null));
        assertThrows(NullPointerException.class, () -> Goal.of((BooleanSupplier) null));
        assertThrows(NullPointerException.class, () -> new Goal.Of(null), "else its check would always say yes");
        assertThrows(IllegalArgumentException.class, () -> new Goal.Done(() -> HOME.claim, Duration.ZERO));
    }

    /** Goals loaded first, reaching Screens only when asked. */
    static final class GoalsFirst {
        static final Goal CLAIMED = Goal.done(() -> ScreensAfter.HOME.claim);
    }

    static final class ScreensAfter {
        static final Serving HOME = new Serving();

        static final class Serving {
            final Do claim = Do.press(MapFixtures.click("chest")).serves(GoalsFirst.CLAIMED);
        }
    }

    /** Screens loaded first, which load Goals as a field is built. */
    static final class ScreensFirst {
        static final Serving HOME = new Serving();

        static final class Serving {
            final Do claim = Do.press(MapFixtures.click("chest")).serves(GoalsAfter.CLAIMED);
        }
    }

    static final class GoalsAfter {
        static final Goal CLAIMED = Goal.done(() -> ScreensFirst.HOME.claim);
    }

    @Test
    void screensAndGoalsNamingEachOtherLoadEitherWay() {
        assertSame(GoalsFirst.CLAIMED, ScreensAfter.HOME.claim.serves().getFirst());
        assertNotNull(((Goal.Done) GoalsFirst.CLAIMED).action().get());

        assertSame(GoalsAfter.CLAIMED, ScreensFirst.HOME.claim.serves().getFirst());
        assertSame(ScreensFirst.HOME.claim, ((Goal.Done) GoalsAfter.CLAIMED).action().get());
    }
}
