package com.botmaker.sdk.internal.plan;

import com.botmaker.plugin.api.managed.RunHostValues;
import com.botmaker.plugin.api.source.ManagedValue;
import com.botmaker.sdk.api.screens.Answer;
import com.botmaker.sdk.api.screens.Do;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;

import static com.botmaker.sdk.internal.screens.MapFixtures.click;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DoneRecordsTest {

    private static final Instant NOON = Instant.parse("2026-11-06T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOON, ZoneOffset.UTC);
    private static final String GAME = "emulator-app:com.example.game";

    @AfterEach
    void unserve() {
        RunHostValues.serve(null);
    }

    @Test
    void keepsAndReads(@TempDir Path dir) {
        DoneRecords records = DoneStores.at(dir, CLOCK);

        records.done(DoneRecords.key("MARKET", "claim"), true);
        records.done(DoneRecords.key("ARENA", "fight"), false);
        records.done(DoneRecords.key("ARENA", "fight"), false);

        DoneRecords again = DoneStores.at(dir, CLOCK);
        assertEquals(new DoneRecords.Done(NOON, true, 0), again.last("MARKET.claim").orElseThrow());
        assertEquals(new DoneRecords.Done(NOON, false, 2), again.last("ARENA.fight").orElseThrow(), "counted in a row");

        records.done("ARENA.fight", true);
        assertEquals(0, DoneStores.at(dir, CLOCK).last("ARENA.fight").orElseThrow().fails(), "a hold ends the count");
    }

    @Test
    void aPressIsKeptWithWhatItsCheckSays(@TempDir Path dir) {
        DoneRecords records = DoneStores.at(dir, CLOCK);
        Do plain = Do.press(click("claim"));

        records.pressed("A.plain", plain, true);
        records.pressed("A.lost", plain, false);
        records.pressed("A.no", plain.checks(() -> Answer.NO), true);
        records.pressed("A.unknown", plain.checks(() -> Answer.UNKNOWN), false);

        assertTrue(records.last("A.plain").orElseThrow().held());
        assertFalse(records.last("A.lost").orElseThrow().held(), "it led elsewhere than the map says");
        assertFalse(records.last("A.no").orElseThrow().held());
        assertTrue(records.last("A.unknown").orElseThrow().held(), "a check says more than where the game went");
    }

    @Test
    void anInstanceReadsOnlyItsOwnRowsOfItsGame() {
        HostDone kept = HostDone.of(
                new HostDone.Row("ld/1", GAME, "HOME.claim", NOON.toString(), true, 0),
                new HostDone.Row("ld/2", GAME, "HOME.claim", NOON.minusSeconds(60).toString(), false, 3),
                new HostDone.Row("ld/1", "steam:570", "HOME.claim", NOON.toString(), false, 1),
                new HostDone.Row("ld/1", GAME, "HOME.fight", "yesterday", true, 0));

        DoneRecords one = DoneRecords.shown(kept, "ld/1", GAME, CLOCK);

        assertEquals(new DoneRecords.Done(NOON, true, 0), one.last("HOME.claim").orElseThrow());
        assertTrue(one.last("HOME.fight").isEmpty(), "a time that does not read is no record");
        assertEquals(3, DoneRecords.shown(kept, "ld/2", GAME, CLOCK).last("HOME.claim").orElseThrow().fails());
    }

    @Test
    void twoRunsOnTwoInstancesKeepEachOthersRows() {
        Kept service = new Kept();
        RunHostValues.serve(service);
        DoneRecords first = new DoneRecords(new DoneRecords.HostStore("ld/1", GAME), CLOCK);
        DoneRecords second = new DoneRecords(new DoneRecords.HostStore("ld/2", GAME), CLOCK);

        first.done("HOME.claim", true);
        second.done("HOME.claim", false);
        first.done("ARENA.fight", true);

        assertEquals(3, service.value.list().size(), "neither run's write took the other's rows");
        assertEquals(Map.of("HOME.claim", new DoneRecords.Done(NOON, false, 1)),
                DoneRecords.rows(service.value, "ld/2", GAME));
        DoneRecords again = new DoneRecords(new DoneRecords.HostStore("ld/1", GAME), CLOCK);
        assertTrue(again.last("ARENA.fight").orElseThrow().held(), "the next run on the instance reads it");
    }

    @Test
    void withNoHostARunKeepsItsOwnMemory() {
        DoneRecords records = new DoneRecords(new DoneRecords.HostStore("ld/1", GAME), CLOCK);

        records.done("MARKET.claim", true);

        assertTrue(records.last("MARKET.claim").isPresent(), "the run keeps its own memory of it");
    }

    @Test
    void theStudioReadingWritesNothing() {
        DoneRecords shown = DoneRecords.shown(null, "", "", CLOCK);
        shown.done("A.a", true);
        assertEquals(Optional.of(new DoneRecords.Done(NOON, true, 0)), shown.last("A.a"));
        assertEquals(List.of(), DoneRecords.replaced(null, "", "", Map.of()).list());
    }

    /** A host keeping one value in memory, as the run's agent would in a folder. */
    private static final class Kept implements RunHostValues.Service {
        HostDone value = HostDone.none();

        @Override
        public <T> Optional<T> read(Class<?> caller, ManagedValue<T> value) {
            return Optional.of(value.cast(this.value));
        }

        @Override
        public synchronized <T> Optional<String> update(Class<?> caller, ManagedValue<T> value, UnaryOperator<T> change) {
            this.value = (HostDone) change.apply(value.cast(this.value));
            return Optional.empty();
        }
    }
}
