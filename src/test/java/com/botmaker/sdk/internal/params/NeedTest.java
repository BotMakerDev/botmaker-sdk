package com.botmaker.sdk.internal.params;

import com.botmaker.java.grammar.ParamsFitter;
import com.botmaker.sdk.api.params.Params;
import com.botmaker.sdk.api.plan.Need;
import com.botmaker.sdk.api.screens.Answer;
import com.botmaker.sdk.api.screens.Condition;
import com.botmaker.sdk.internal.params.fixture.Screens;
import com.botmaker.sdk.internal.params.fixture.params.bot.Hero;
import com.botmaker.sdk.internal.params.fixture.params.bot.Progress;
import com.botmaker.sdk.internal.screens.ScreenGraph;
import com.botmaker.sdk.internal.screens.ScreenMaps;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** {@link Need} answers in three, over a bot's parameters and its facts. */
class NeedTest {

    private static final String PACKAGE = "com/botmaker/sdk/internal/params/fixture/params";

    @TempDir
    Path bot;

    private final AtomicLong now = new AtomicLong(1_000_000L);

    @BeforeEach
    void aBotWithItsGroups() throws IOException {
        for (String group : List.of("bot/Progress.java", "bot/Hero.java")) {
            String source = Files.readString(Path.of("src/test/java", PACKAGE, group));
            for (Path root : List.of(bot.resolve("src/main/java").resolve(PACKAGE), bot.resolve(ParamsFitter.STARTING))) {
                Path file = root.resolve(group);
                Files.createDirectories(file.getParent());
                Files.writeString(file, source);
            }
        }
        RunParams.startOver(bot);
        RunParams.clock(now::get);
        ScreenMaps.use(ScreenGraph.ofScreens(Map.of("HOME", new Screens.Home())));
    }

    @AfterEach
    void noMapLeft() {
        ScreenMaps.use(null);
        RunParams.clock(System::currentTimeMillis);
    }

    @Test
    void numbersCompareThreeValued() {
        Params.set(Progress::level, 5);

        assertEquals(Answer.YES, Need.atLeast(Progress::level, 5).answer());
        assertEquals(Answer.NO, Need.atLeast(Progress::level, 6).answer());
        assertEquals(Answer.YES, Need.atMost(Progress::level, 5).answer());
        assertEquals(Answer.NO, Need.atMost(Progress::level, 4).answer());
        assertEquals(Answer.YES, Need.above(Progress::level, 4).answer());
        assertEquals(Answer.NO, Need.above(Progress::level, 5).answer());
        assertEquals(Answer.YES, Need.below(Progress::level, 6).answer());
        assertEquals(Answer.NO, Need.below(Progress::level, 5).answer());
    }

    @Test
    void booleansAndAnyValue() {
        Params.set(Progress::open, true);
        Params.set(Progress::rank, Progress.Rank.HIGH);

        assertEquals(Answer.YES, Need.is(Progress::open).answer());
        assertEquals(Answer.NO, Need.not(Progress::open).answer());
        assertEquals(Answer.YES, Need.equalTo(Progress::rank, Progress.Rank.HIGH).answer());
        assertEquals(Answer.NO, Need.equalTo(Progress::rank, Progress.Rank.LOW).answer());
    }

    @Test
    void unknownFactIsUnknown() {
        for (Condition need : List.of(Need.atLeast(Hero::level, 1), Need.atMost(Hero::level, 1),
                Need.above(Hero::level, 1), Need.below(Hero::level, 1), Need.equalTo(Hero::level, 1),
                Need.is(Hero::badge), Need.not(Hero::badge))) {
            assertEquals(Answer.UNKNOWN, need.answer(), "a fact never read");
        }
    }

    @Test
    void aStaleFactIsUnknownAgain() {
        Params.set(Hero::level, 12);
        assertEquals(Answer.YES, Need.above(Hero::level, 1).answer());

        now.addAndGet(61 * 60_000L);

        assertEquals(Answer.UNKNOWN, Need.above(Hero::level, 1).answer(), "past its @StaleAfter");
        assertEquals(Answer.UNKNOWN, Need.atLeast(Hero::level, 1).answer());
    }

    @Test
    void refusesWhatCouldNeverAnswer() {
        assertThrows(IllegalArgumentException.class, () -> Need.atLeast(p -> 5, 1), "a lambda");
        assertThrows(IllegalArgumentException.class, () -> Need.atLeast(String::length, 1), "no @Param class");
        assertThrows(NullPointerException.class, () -> Need.atLeast(Progress::level, null));
        assertThrows(NullPointerException.class, () -> Need.equalTo(Progress::rank, null));
    }
}
