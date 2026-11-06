package com.botmaker.sdk.internal.plan;

import com.botmaker.sdk.api.screens.Answer;
import com.botmaker.sdk.api.screens.Do;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static com.botmaker.sdk.internal.screens.MapFixtures.click;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DoneRecordsTest {

    private static final Instant NOON = Instant.parse("2026-11-06T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOON, ZoneOffset.UTC);

    @Test
    void writesAndReads(@TempDir Path dir) {
        Path file = dir.resolve("state").resolve("done.json");
        DoneRecords records = new DoneRecords(file, CLOCK);

        records.done(DoneRecords.key("MARKET", "claim"), true);
        records.done(DoneRecords.key("ARENA", "fight"), false);
        records.done(DoneRecords.key("ARENA", "fight"), false);

        DoneRecords again = new DoneRecords(file, CLOCK);
        assertEquals(new DoneRecords.Done(NOON, true, 0), again.last("MARKET.claim").orElseThrow());
        assertEquals(new DoneRecords.Done(NOON, false, 2), again.last("ARENA.fight").orElseThrow(), "counted in a row");

        records.done("ARENA.fight", true);
        assertEquals(0, new DoneRecords(file, CLOCK).last("ARENA.fight").orElseThrow().fails(), "a hold ends the count");
        try (var files = Files.list(file.getParent())) {
            assertEquals(1, files.count(), "no temporary file left");
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void aPressIsKeptWithWhatItsCheckSays(@TempDir Path dir) {
        DoneRecords records = new DoneRecords(dir.resolve("done.json"), CLOCK);
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
    void missingFileIsEmpty(@TempDir Path dir) {
        assertEquals(Optional.empty(), new DoneRecords(dir.resolve("none.json"), CLOCK).last("MARKET.claim"));
        assertFalse(Files.exists(dir.resolve("none.json")), "reading writes nothing");
    }

    @Test
    void anUnreadableFileIsEmptyAndARecordItLacksIsSkipped(@TempDir Path dir) throws IOException {
        Path broken = dir.resolve("broken.json");
        Files.writeString(broken, "{ not json");
        assertTrue(new DoneRecords(broken, CLOCK).last("MARKET.claim").isEmpty());

        Path partly = dir.resolve("partly.json");
        Files.writeString(partly, """
                {"A.a": {"at": "yesterday"}, "B.b": {"at": "2026-11-06T12:00:00Z", "held": true, "fails": 0}}""");
        DoneRecords read = new DoneRecords(partly, CLOCK);
        assertTrue(read.last("A.a").isEmpty());
        assertTrue(read.last("B.b").isPresent());
    }

    @Test
    void aFileThatCannotBeWrittenEndsNothing(@TempDir Path dir) throws IOException {
        Path blocked = Files.writeString(dir.resolve("blocked"), "a file, where a folder is needed");
        DoneRecords records = new DoneRecords(blocked.resolve("done.json"), CLOCK);

        records.done("MARKET.claim", true);

        assertTrue(records.last("MARKET.claim").isPresent(), "the run keeps its own memory of it");
    }
}
