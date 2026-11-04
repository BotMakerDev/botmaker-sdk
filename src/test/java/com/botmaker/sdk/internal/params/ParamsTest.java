package com.botmaker.sdk.internal.params;

import com.botmaker.java.grammar.ParamsFitter;
import com.botmaker.sdk.api.params.Params;
import com.botmaker.sdk.api.screens.Answer;
import com.botmaker.sdk.internal.params.fixture.params.bot.Progress;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A run writes its own during-a-run parameters (2026-11-04). */
class ParamsTest {

    private static final String RELATIVE = "bot/Progress.java";
    private static final String PACKAGE = "com/botmaker/sdk/internal/params/fixture/params";

    @TempDir
    Path bot;

    private Path file;

    @BeforeEach
    void aBotWithItsGroup() throws IOException {
        String source = Files.readString(Path.of("src/test/java", PACKAGE, RELATIVE));
        file = bot.resolve("src/main/java").resolve(PACKAGE).resolve(RELATIVE);
        Files.createDirectories(file.getParent());
        Files.writeString(file, source);
        Path starting = bot.resolve(ParamsFitter.STARTING).resolve(RELATIVE);
        Files.createDirectories(starting.getParent());
        Files.writeString(starting, source);
        RunParams.startOver(bot);
    }

    @Test
    void aSetValueIsReadBackNextRun() throws IOException {
        Params.set(Progress::level, 5);

        assertEquals(5, Params.get(Progress::level));
        String written = Files.readString(file);
        assertTrue(written.contains("return 5;"), written);
        assertTrue(written.contains("return 10;"), written);
        String starting = Files.readString(bot.resolve(ParamsFitter.STARTING).resolve(RELATIVE));
        assertTrue(starting.contains("return 1;"), "a value never reaches the starting copy");
        assertFalse(Files.exists(file.resolveSibling("Progress.java.tmp")));
    }

    @Test
    void aValueOfAnotherTypeIsRefusedAndNeverWritten() throws IOException {
        String before = Files.readString(file);

        assertThrows(IllegalArgumentException.class, () -> Params.set(Progress::level, "System.exit(0)"));
        assertThrows(IllegalArgumentException.class, () -> Params.set(Progress::level, null));

        assertEquals(before, Files.readString(file));
    }

    @Test
    void valuesOfEveryKindAreSpelled() throws IOException {
        Params.set(Progress::rank, Progress.Rank.HIGH);
        Params.set(Progress::open, true);
        Params.set(Progress::rest, Duration.ofSeconds(30));

        String written = Files.readString(file);
        assertTrue(written.contains("Rank.HIGH"), written);
        assertTrue(written.contains("return true;"), written);
        assertTrue(written.contains("Duration.ofMillis(30000L)"), written);
    }

    @Test
    void anUnwritableValueStaysInMemory() throws IOException {
        String before = Files.readString(file);
        Object value = new Object();

        Params.set(Progress::unspellable, value);

        assertEquals(value, Params.get(Progress::unspellable));
        assertEquals(before, Files.readString(file));
    }

    /** Text a run read off a game is never written as Java, under a type that takes its source elsewhere. */
    @Test
    void aRunsTextIsNeverWrittenAsJava() throws IOException {
        String before = Files.readString(file);
        Params.Of<Progress, Object> sources = Progress::sources;

        Params.set(sources, List.of("System.exit(0)"));

        assertEquals(before, Files.readString(file));
    }

    @Test
    void aMissingFileLeavesTheValueInMemory() throws IOException {
        Files.delete(file);
        Files.delete(bot.resolve(ParamsFitter.STARTING).resolve(RELATIVE));

        Params.set(Progress::level, 9);

        assertEquals(9, Params.get(Progress::level));
    }

    @Test
    void aParameterEditedOutsideARunCannotBeSet() {
        assertThrows(IllegalArgumentException.class, () -> Params.set(Progress::tries, 3));
    }

    @Test
    void aLambdaIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> Params.get((Progress p) -> p.tries()));
    }

    @Test
    void anUnsetParameterReadsItsMethod() {
        assertEquals(10, Params.get(Progress::tries));
    }

    @Test
    void aConditionReadsTheValueEachTime() {
        var atLeastFive = Params.when(Progress::level).atLeast(5);
        var atMostFive = Params.when(Progress::level).atMost(5);
        var isRank = Params.when(Progress::rank).is(Progress.Rank.HIGH);
        var open = Params.when(Progress::open).isTrue();
        assertEquals(Answer.NO, atLeastFive.answer());
        assertEquals(Answer.YES, atMostFive.answer());
        assertEquals(Answer.NO, isRank.answer());
        assertEquals(Answer.NO, open.answer());

        Params.set(Progress::level, 6);
        Params.set(Progress::rank, Progress.Rank.HIGH);
        Params.set(Progress::open, true);

        assertEquals(Answer.YES, atLeastFive.answer());
        assertEquals(Answer.NO, atMostFive.answer());
        assertEquals(Answer.YES, isRank.answer());
        assertEquals(Answer.YES, open.answer());
    }

    @Test
    void aValueThatCannotBeComparedIsUnknown() {
        assertEquals(Answer.UNKNOWN, Params.when(Progress::unspellable).is("x").answer());
        assertEquals(Answer.UNKNOWN, Params.when(Progress::tries).isTrue().answer());
    }
}
