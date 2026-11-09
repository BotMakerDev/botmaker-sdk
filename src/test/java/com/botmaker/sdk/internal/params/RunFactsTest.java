package com.botmaker.sdk.internal.params;

import com.botmaker.java.grammar.ParamsFitter;
import com.botmaker.sdk.api.params.Params;
import com.botmaker.sdk.api.screens.Answer;
import com.botmaker.sdk.internal.config.ParamGroups;
import com.botmaker.sdk.internal.params.fixture.Screens;
import com.botmaker.sdk.internal.params.fixture.params.bot.Hero;
import com.botmaker.sdk.internal.params.fixture.params.bot.Progress;
import com.botmaker.sdk.internal.params.fixture.params.bot.Veteran;
import com.botmaker.sdk.internal.params.fixture.params.bot.Wallet;
import com.botmaker.sdk.internal.params.fixture.plugins.Holder;
import com.botmaker.sdk.internal.screens.Reach;
import com.botmaker.sdk.internal.screens.ScreenGraph;
import com.botmaker.sdk.internal.screens.ScreenMaps;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Facts are unknown until written, read on their screen when stale, and kept with their time (2026-11-04); a readout
 * that names a fact says where and how long, and wins over a deprecated {@code @ReadOn} (2026-11-06).
 */
class RunFactsTest {

    private static final String PACKAGE = "com/botmaker/sdk/internal/params/fixture/params";
    private static final Instant NOW = Instant.parse("2026-11-04T10:00:00Z");
    private static final Map<String, String> SHOWN = Map.of("level", "Lv 12", "player", "Fire mage");

    @TempDir
    Path bot;

    private final AtomicLong now = new AtomicLong(NOW.toEpochMilli());
    private final List<List<String>> reads = new ArrayList<>();
    private Path heroFile;

    @BeforeEach
    void aBotWithItsFacts() throws IOException {
        for (String group : List.of("bot/Hero.java", "bot/Veteran.java", "bot/Wallet.java")) {
            String source = Files.readString(Path.of("src/test/java", PACKAGE, group));
            Path file = bot.resolve("src/main/java").resolve(PACKAGE).resolve(group);
            Files.createDirectories(file.getParent());
            Files.writeString(file, source);
            Path starting = bot.resolve(ParamsFitter.STARTING).resolve(group);
            Files.createDirectories(starting.getParent());
            Files.writeString(starting, source);
        }
        heroFile = bot.resolve("src/main/java").resolve(PACKAGE).resolve("bot/Hero.java");
        RunParams.startOver(bot);
        RunParams.clock(now::get);
        ScreenMaps.use(ScreenGraph.ofScreens(Map.of("HOME", new Screens.Home(), "SHOP", new Screens.Shop())));
        showing(Optional.of(SHOWN));
    }

    @AfterEach
    void noMapLeft() {
        RunFacts.use(List.of(), (constant, fields) -> Optional.empty());
        ScreenMaps.use(null);
    }

    private void showing(Optional<Map<String, String>> look) {
        RunFacts.use(List.of(Hero.class), (constant, fields) -> {
            reads.add(fields);
            return look;
        });
    }

    @Test
    void anUnknownFactIsUnknown() {
        var atLeastOne = Params.when(Hero::level).atLeast(1);

        assertEquals(Optional.empty(), Params.known(Hero::level));
        assertEquals(Answer.UNKNOWN, atLeastOne.answer());
        assertEquals(Optional.empty(), Params.known(Hero::tutorialDone), "a fact no screen shows, never set");

        Params.set(Hero::level, 5);

        assertEquals(Optional.of(5), Params.known(Hero::level));
        assertEquals(Answer.YES, atLeastOne.answer());
    }

    @Test
    void anAssumedFactAnswersUntilRead() {
        assertEquals(Optional.of(3), Params.known(Hero::gold));

        Params.set(Hero::gold, 9);

        assertEquals(Optional.of(9), Params.known(Hero::gold));
    }

    @Test
    void aStaleFactIsReadOnItsScreenOnly() {
        RunFacts.seen("SHOP");
        assertTrue(reads.isEmpty(), "not its screen");

        RunFacts.seen("HOME");
        assertEquals(1, reads.size(), "all off one look");
        assertEquals(Optional.of(12), Params.known(Hero::level));
        assertEquals(Optional.of(Hero.Kind.FIRE_MAGE), Params.known(Hero::kind), "read loosely");

        RunFacts.seen("HOME");
        assertEquals(1, reads.size(), "fresh: not read again");

        now.addAndGet(61 * 60_000L);
        assertEquals(Optional.empty(), Params.known(Hero::level), "stale");
        RunFacts.seen("SHOP");
        assertEquals(1, reads.size());
        assertEquals(Optional.empty(), Params.known(Hero::level), "still stale: its screen was not seen");

        RunFacts.seen("HOME");
        assertEquals(2, reads.size());
        assertEquals(Optional.of(12), Params.known(Hero::level), "the same value, a new time");
    }

    @Test
    void theSameValueReadAgainSoonIsFreshThoughNotWritten() throws IOException, NoSuchMethodException {
        RunFacts.seen("HOME");
        now.addAndGet(10_000);
        RunFacts.readAgain("HOME", Set.of(Hero.class.getMethod("level")));

        assertEquals(Optional.of(NOW.plusSeconds(10)), RunFacts.readAt(Hero.class.getMethod("level")),
                "the run knows the second reading's time");
        assertTrue(Files.readString(heroFile).contains("@Param.Kept(at = \"2026-11-04T10:00:00Z\")"),
                "the file keeps the first: only a time would change");
    }

    @Test
    void readEveryReadsOnlyWhatWasNotReadSince() {
        RunFacts.seen("HOME");
        assertEquals(1, reads.size());

        RunFacts.readEvery("HOME", NOW);
        assertEquals(1, reads.size(), "read at the look's start: not read twice");

        now.addAndGet(1_000);
        RunFacts.readEvery("HOME", NOW.plusSeconds(1));
        assertEquals(2, reads.size(), "fresh but read before the look: read again");
    }

    @Test
    void forgetRemovesTheValue() throws IOException {
        Params.set(Hero::level, 9);
        String written = Files.readString(heroFile);
        assertTrue(written.contains("return 9;") && written.contains("Param.Kept"), written);

        Params.forget(Hero::level);

        String forgotten = Files.readString(heroFile);
        assertFalse(forgotten.contains("return 9;"), forgotten);
        assertFalse(forgotten.contains("Param.Kept"), forgotten);
        assertEquals(Optional.empty(), Params.known(Hero::level));

        Params.set(Hero::gold, 8);
        Params.forget(Hero::gold);
        assertEquals(Optional.of(3), Params.known(Hero::gold), "assumed again");
    }

    @Test
    void aForgottenFactCompiledWithATimeStaysUnknownThisRun() {
        Params.forget(Veteran::flagged);

        assertEquals(Optional.empty(), Params.known(Veteran::flagged));
    }

    @Test
    void aLookThatMissesTheScreenKeepsNothing() {
        showing(Optional.empty());

        RunFacts.seen("HOME");

        assertEquals(1, reads.size());
        assertEquals(Optional.empty(), Params.known(Hero::level));
        assertEquals(Optional.empty(), Params.known(Hero::badge), "not shown is not known from a look that missed");
    }

    /** A look whose words could not be read keeps nothing: a flag is unknown, never "not shown". */
    @Test
    void aFailedReadOfWordsKeepsNothing() {
        RunFacts.use(List.of(Hero.class), (constant, fields) -> {
            throw new Reach.TextUnread(new IllegalStateException("tesseract failed"));
        });

        RunFacts.seen("HOME");

        assertEquals(Optional.empty(), Params.known(Hero::badge));
        assertEquals(Optional.empty(), Params.known(Hero::level));
    }

    @Test
    void aMisnamedReadoutNeverReadsAsFalse() {
        showing(Optional.of(Map.of()));

        RunFacts.seen("HOME");

        assertEquals(Optional.of(false), Params.known(Hero::badge), "its readout exists and shows nothing");
        assertEquals(Optional.empty(), Params.known(Hero::gone), "it has no readout");
        assertFalse(reads.getFirst().contains("gone"), reads.toString());
    }

    @Test
    void aFactKeptLongAgoIsStaleAndAFlagIsNot() {
        assertEquals(Optional.empty(), Params.known(Veteran::read), "read in 2020");
        assertEquals(Optional.of(true), Params.known(Veteran::flagged), "nothing makes a flag stale");
    }

    @Test
    void aReadValueIsWrittenWithItsTime() throws IOException {
        RunFacts.seen("HOME");

        String written = Files.readString(heroFile);
        assertTrue(written.contains("return 12;"), written);
        assertTrue(written.contains("@Param.Kept(at = \"2026-11-04T10:00:00Z\")"), written);
        assertTrue(written.contains("Kind.FIRE_MAGE"), written);
    }

    /** Wallet's coins is fed by the shop's readout and marked @ReadOn the home screen: the readout wins. */
    @Test
    void readoutWinsOverReadOn() {
        RunFacts.use(List.of(Hero.class, Wallet.class), (constant, fields) -> {
            reads.add(fields);
            return Optional.of(Map.of("level", "Lv 12", "coins", "40"));
        });

        RunFacts.seen("HOME");
        assertEquals(Optional.empty(), Params.known(Wallet::coins), "not read where @ReadOn says");
        assertFalse(reads.getFirst().contains("coins"), reads.toString());

        RunFacts.seen("SHOP");
        assertEquals(Optional.of(40), Params.known(Wallet::coins), "read where its readout is");
    }

    /** A reading holds what its readout's staleAfter says, here ten minutes, not the hour a readout holds alone. */
    @Test
    void staleAfterMakesItUnknown() {
        RunFacts.use(List.of(Wallet.class), (constant, fields) -> Optional.of(Map.of("coins", "40", "owner", "Ann")));
        RunFacts.seen("SHOP");
        assertEquals(Optional.of(40), Params.known(Wallet::coins));
        assertEquals(Optional.of("Ann"), Params.known(Wallet::owner));

        now.addAndGet(11 * 60_000L);

        assertEquals(Optional.empty(), Params.known(Wallet::coins), "past its ten minutes");
        assertEquals(Optional.of("Ann"), Params.known(Wallet::owner), "an hour by default");
        now.addAndGet(50 * 60_000L);
        assertEquals(Optional.empty(), Params.known(Wallet::owner), "past the hour");
    }

    /** A readout that says no time leaves the fact's deprecated {@code @StaleAfter} in force, not the hour. */
    @Test
    void aReadoutWithNoTimeKeepsTheOldStaleAfter() {
        RunFacts.use(List.of(Wallet.class), (constant, fields) -> Optional.of(Map.of("tips", "7")));
        RunFacts.seen("SHOP");
        assertEquals(Optional.of(7), Params.known(Wallet::tips));

        now.addAndGet(6 * 60_000L);

        assertEquals(Optional.empty(), Params.known(Wallet::tips), "past its five minutes");
    }

    /** One screen feeds a fact once: a second readout's absence must not overwrite the first's reading. */
    @Test
    void twoReadoutsOfOneScreenFeedAFactOnce() {
        RunFacts.use(List.of(Wallet.class), (constant, fields) -> {
            reads.add(fields);
            return Optional.of(Map.of());
        });

        RunFacts.seen("SHOP");

        assertEquals(1, reads.getFirst().stream().filter(field -> field.startsWith("sale")).count(), reads.toString());
    }

    @Test
    void aFactGroupIsFoundBesideItsHolder() {
        List<Class<?>> found = ParamGroups.allBeside(Holder.class);

        assertTrue(found.containsAll(List.of(Hero.class, Veteran.class, Progress.class)), found.toString());
        assertFalse(found.contains(Hero.Kind.class), "only top-level classes");
        assertEquals(List.of(), ParamGroups.allBeside(String.class), "not a bot's holder");
    }
}
