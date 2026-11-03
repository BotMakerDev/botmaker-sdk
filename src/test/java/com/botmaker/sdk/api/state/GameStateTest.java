package com.botmaker.sdk.api.state;

import com.botmaker.sdk.api.geometry.Zone;
import com.botmaker.sdk.api.screens.Readout;
import com.botmaker.sdk.api.screens.Screen;
import com.botmaker.sdk.api.screens.Setup;
import com.botmaker.sdk.internal.screens.ScreenGraph;
import com.botmaker.sdk.internal.screens.ScreenMaps;
import com.botmaker.sdk.internal.state.KeptFacts;
import com.botmaker.sdk.internal.state.StateFile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static com.botmaker.sdk.api.screens.Answer.NO;
import static com.botmaker.sdk.api.screens.Answer.UNKNOWN;
import static com.botmaker.sdk.api.screens.Answer.YES;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Game facts are kept per target and account, read on their screen when stale, and unknown otherwise. */
class GameStateTest {

    public static final class Home extends Screen {
        public final Readout level = Readout.text(new Zone(0, 0, 40, 20));
        public final Readout player = Readout.text(new Zone(0, 20, 80, 20));

        @Override
        public Setup setup() {
            return Setup.named("Home");
        }
    }

    public static final class Shop extends Screen {
        @Override
        public Setup setup() {
            return Setup.named("Shop");
        }
    }

    enum HeroClass { WARRIOR, FIRE_MAGE }

    static final Fact<Integer> LEVEL = Fact.number(Home.class, h -> h.level).staleAfter(Duration.ofHours(1));
    static final Fact<Boolean> TUTORIAL_DONE = Fact.set();
    static final Fact<Integer> GOLD = Fact.number(Home.class, h -> h.level).assume(3);
    static final Fact<String> PLAYER = Fact.read(Home.class, h -> h.player).account();
    static final Fact<HeroClass> HERO = Fact.read(Home.class, h -> h.player).as(HeroClass.class);

    @TempDir
    Path bot;

    private final AtomicLong now = new AtomicLong(1_000_000);
    private final List<String> reads = new ArrayList<>();
    private final Map<String, String> shows = new LinkedHashMap<>();

    @BeforeEach
    void map() {
        ScreenMaps.use(ScreenGraph.ofScreens(Map.of("HOME", new Home(), "SHOP", new Shop())));
        run(Map.of("LEVEL", LEVEL, "TUTORIAL_DONE", TUTORIAL_DONE, "GOLD", GOLD));
    }

    @AfterEach
    void clear() {
        KeptFacts.use(Map.of(), bot, () -> null, now::get, (c, f) -> Optional.empty());
        ScreenMaps.use(null);
    }

    /** A new run of the bot: its facts read again from the file. */
    private void run(Map<String, Fact<?>> facts) {
        KeptFacts.use(facts, bot, () -> "emulator:com.game@waydroid", now::get, (constant, fields) -> {
            if (!onScreen) return Optional.empty();
            Map<String, String> read = new LinkedHashMap<>();
            for (String field : fields) {
                reads.add(constant + "." + field);
                if (shows.containsKey(field)) read.put(field, shows.get(field));
            }
            return Optional.of(read);
        });
    }

    /** Whether the look the reader takes shows the screen just seen. */
    private boolean onScreen = true;

    @Test
    void theAccountIsReadFirstAndOnEachArrival() {
        Map<String, Fact<?>> facts = new LinkedHashMap<>();
        facts.put("LEVEL", LEVEL);
        facts.put("PLAYER", PLAYER);
        run(facts);
        shows.put("level", "5");
        shows.put("player", "Alice");
        ScreenMaps.seen("HOME");
        shows.put("level", "7");
        shows.put("player", "Bob");
        now.addAndGet(Duration.ofMinutes(10).toMillis());
        ScreenMaps.seen("HOME");
        assertEquals(Optional.of("Bob"), GameState.get(PLAYER), "a fresh account is read again");
        assertEquals(Optional.of(7), GameState.get(LEVEL), "Bob's level, read for Bob");
        GameState.set(PLAYER, "Alice");
        assertEquals(Optional.of(5), GameState.get(LEVEL), "Alice's level stayed hers");
    }

    @Test
    void aLookThatMissesTheScreenKeepsNothing() {
        Fact<Boolean> badge = Fact.shown(Home.class, h -> h.player);
        run(Map.of("BADGE", badge));
        onScreen = false;
        ScreenMaps.seen("HOME");
        assertEquals(Optional.empty(), GameState.get(badge), "not a false: the screen was not seen");
        onScreen = true;
        ScreenMaps.seen("HOME");
        assertEquals(Optional.of(false), GameState.get(badge), "seen, and nothing read there");
    }

    @Test
    void aReadoutThatThrowsLeavesTheFactUnknown() {
        Fact<Integer> broken = Fact.number(Home.class, h -> { throw new IllegalStateException("renamed"); });
        run(Map.of("BROKEN", broken, "LEVEL", LEVEL));
        shows.put("level", "3");
        ScreenMaps.seen("HOME");
        assertEquals(Optional.empty(), GameState.get(broken));
        assertEquals(Optional.of(3), GameState.get(LEVEL), "the other facts of the screen are still read");
    }

    @Test
    void aFactNeverReadIsUnknown() {
        assertEquals(Optional.empty(), GameState.get(LEVEL));
        assertEquals(UNKNOWN, LEVEL.atLeast(5).answer());
        assertTrue(LEVEL.atLeast(5).getAsBoolean(), "unknown never closes a way");
        assertEquals(UNKNOWN, TUTORIAL_DONE.isTrue().answer());
    }

    @Test
    void anAssumedFactAnswersUntilRead() {
        assertEquals(Optional.of(3), GameState.get(GOLD));
        assertEquals(YES, GOLD.atMost(3).answer());
        GameState.set(GOLD, 7);
        assertEquals(Optional.of(7), GameState.get(GOLD));
        assertEquals(NO, GOLD.atMost(3).answer());
    }

    @Test
    void twoAccountsKeepTheirOwnFacts() {
        run(Map.of("PLAYER", PLAYER, "LEVEL", LEVEL));
        GameState.set(PLAYER, "Alice");
        GameState.set(LEVEL, 5);
        GameState.set(PLAYER, "Bob");
        assertEquals(Optional.empty(), GameState.get(LEVEL));
        GameState.set(LEVEL, 9);
        GameState.set(PLAYER, "Alice");
        assertEquals(Optional.of(5), GameState.get(LEVEL));

        Path sdk = StateFile.folder(bot);
        assertTrue(Files.isRegularFile(StateFile.game(sdk, "emulator:com.game@waydroid", null)));
        assertTrue(Files.isRegularFile(StateFile.game(sdk, "emulator:com.game@waydroid", "alice")));
        assertTrue(Files.isRegularFile(StateFile.game(sdk, "emulator:com.game@waydroid", "bob")));

        run(Map.of("PLAYER", PLAYER, "LEVEL", LEVEL));
        assertEquals(Optional.of(5), GameState.get(LEVEL), "the next run plays the account last read");
    }

    @Test
    void aStaleFactIsReadOnItsScreenOnly() {
        shows.put("level", "Lv. 4");
        ScreenMaps.seen("SHOP");
        assertEquals(List.of(), reads);
        ScreenMaps.seen("HOME");
        assertEquals(Optional.of(4), GameState.get(LEVEL));
        int once = reads.size();
        ScreenMaps.seen("HOME");
        assertEquals(once, reads.size(), "a fresh fact is not read again");

        now.addAndGet(Duration.ofHours(2).toMillis());
        assertEquals(Optional.empty(), GameState.get(LEVEL), "stale is unknown");
        shows.put("level", "Lv. 6");
        ScreenMaps.seen("HOME");
        assertEquals(Optional.of(6), GameState.get(LEVEL));
    }

    @Test
    void aCorruptStateFileIsDropped() throws IOException {
        Path file = StateFile.game(StateFile.folder(bot), "emulator:com.game@waydroid", null);
        Files.createDirectories(file.getParent());
        Files.writeString(file, "{not json");
        run(Map.of("LEVEL", LEVEL));
        assertEquals(Optional.empty(), GameState.get(LEVEL));
        GameState.set(LEVEL, 2);
        run(Map.of("LEVEL", LEVEL));
        assertEquals(Optional.of(2), GameState.get(LEVEL), "the file is written whole again");
    }

    @Test
    void anEntryThatDoesNotReadIsDropped() throws IOException {
        Path file = StateFile.game(StateFile.folder(bot), "emulator:com.game@waydroid", null);
        Files.createDirectories(file.getParent());
        Files.writeString(file, "{\"HERO\": {\"value\": \"PALADIN\", \"at\": \"1970-01-01T00:16:40Z\"},"
                + " \"LEVEL\": {\"value\": \"five\", \"at\": \"1970-01-01T00:16:40Z\"}}");
        run(Map.of("HERO", HERO, "LEVEL", LEVEL));
        assertEquals(Optional.empty(), GameState.get(HERO));
        assertEquals(Optional.empty(), GameState.get(LEVEL));
        shows.put("player", "Fire mage");
        ScreenMaps.seen("HOME");
        assertEquals(Optional.of(HeroClass.FIRE_MAGE), GameState.get(HERO));
    }

    @Test
    void aSetFactSurvivesTheNextRun() {
        GameState.set(TUTORIAL_DONE, true);
        now.addAndGet(Duration.ofDays(30).toMillis());
        run(Map.of("TUTORIAL_DONE", TUTORIAL_DONE));
        assertEquals(Optional.of(true), GameState.get(TUTORIAL_DONE), "a step's fact is never stale");
        assertEquals(YES, TUTORIAL_DONE.isTrue().answer());
    }

    @Test
    void forgetMakesItUnknown() {
        GameState.set(LEVEL, 5);
        GameState.set(TUTORIAL_DONE, true);
        GameState.forget(LEVEL);
        assertEquals(Optional.empty(), GameState.get(LEVEL));
        assertEquals(Optional.of(true), GameState.get(TUTORIAL_DONE));
        GameState.set(GOLD, 8);
        GameState.forgetAll();
        assertEquals(Optional.empty(), GameState.get(TUTORIAL_DONE));
        assertEquals(Optional.of(3), GameState.get(GOLD), "forgotten, the assumed value answers again");
    }
}
