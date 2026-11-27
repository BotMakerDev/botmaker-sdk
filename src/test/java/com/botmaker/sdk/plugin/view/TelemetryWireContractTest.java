// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.view;

import com.botmaker.plugin.api.TraceLine;
import com.botmaker.sdk.internal.observe.RunEvent;
import com.botmaker.sdk.plugin.view.TelemetrySerializer.RunState;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The plugin's part of the pilot wire contract: the {@code telemetry.} messages its view draws over the
 * picture, which the phone's {@code types.ts} names field for field.
 *
 * <p>{@code pilot-wire/wire-golden.json} here holds those cases, and still some of the server's messages, which
 * {@code TelemetrySerializer} can also write. The whole corpus is the core's, byte-identical in the pilot repo
 * ({@code web/src/wire-golden.json}); this copy keeps its own digest, so a {@code telemetry.} case changed here
 * must be changed there too.
 */
class TelemetryWireContractTest {

    private static final String GOLDEN = "/pilot-wire/wire-golden.json";

    /** This copy, byte for byte. */
    private static final String GOLDEN_SHA256 = "0b751c506853a217a49ac73b19cefc89f1293f728943f67325c3cfcc4750874c";

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final JsonNode CORPUS = corpus();

    /** Every case this class asserted, so a case added to the corpus and never wired up fails the build. */
    private static final Set<String> COVERED = new HashSet<>();

    private static final RunEvent.Target TARGET =
            new RunEvent.Target("Nested Game", 100, 50, 640, 480);

    // --- The corpus, message by message ---

    @Test
    void aSuccessfulMatchCarriesItsSearchRegionAndTheRectItFound() {
        assertWire("telemetry.match.found", TelemetrySerializer.telemetryJson(new RunEvent.Match(
                TARGET,
                new RunEvent.Rect(110, 60, 200, 100),
                new RunEvent.Rect(150, 80, 32, 24),
                0.9123, true)));
    }

    @Test
    void aFailedMatchStillSendsTheKeysTheClientReadsRatherThanOmittingThem() {
        // region/rect are null, not absent: the renderer tests `o.region` / `o.rect`, so an omitted key
        // would read the same — but the client's `Rect | null` types say null, and this pins it.
        assertWire("telemetry.match.miss",
                TelemetrySerializer.telemetryJson(new RunEvent.Match(TARGET, null, null, 0.0, false)));
    }

    @Test
    void aClickCarriesAbsoluteCoordinatesAndTheButton() {
        assertWire("telemetry.click",
                TelemetrySerializer.telemetryJson(new RunEvent.Click(TARGET, 300, 220, 3)));
    }

    @Test
    void aSwipeCarriesBothEndsAndHowLongItTook() {
        // Both ends in one message, not a stream of moves: the client draws an arrow, and a gesture arriving
        // as fifty little segments would be fifty overlays fading out of step with each other.
        assertWire("telemetry.swipe",
                TelemetrySerializer.telemetryJson(new RunEvent.Swipe(TARGET, 200, 400, 200, 120, 450)));
    }

    @Test
    void aRegionHighlightCarriesOnlyItsRect() {
        assertWire("telemetry.region", TelemetrySerializer.telemetryJson(
                new RunEvent.Region(TARGET, new RunEvent.Rect(0, 0, 640, 480))));
    }

    @Test
    void aTitleWithQuotesBackslashesAndNonAsciiSurvivesIntact() {
        // A window title is whatever the game called itself. Quote and backslash are escaped; a non-ASCII
        // character goes out as itself (the frames are UTF-8), and a negative origin is a left-hand monitor.
        RunEvent.Target odd =
                new RunEvent.Target("He said \"hi\" \u2014 C:\\Games\\x", -1920, 0, 1280, 720);
        assertWire("telemetry.target.title.quoted", TelemetrySerializer.telemetryJson(
                new RunEvent.Region(odd, new RunEvent.Rect(-1920, 0, 100, 100))));
    }

    @Test
    void aNullTargetSerializesAsNullRatherThanCrashing() {
        // RunEvent's javadoc says target is never null; the serializer tolerates it anyway and the
        // client types it `Target | null`. Characterising it here means the tolerance is deliberate.
        assertWire("telemetry.target.null",
                TelemetrySerializer.telemetryJson(new RunEvent.Click(null, 10, 20, 1)));
    }

    @Test
    void aTraceLineCarriesWhenHowSeriousWhatWroteItWhatItSaidAndHowOften() {
        // The source line, the writer and the region stay on the host: a phone's log drawer shows the words.
        assertWire("trace.debug", TelemetrySerializer.traceJson(new TraceLine(
                Instant.ofEpochMilli(1_700_000_000_123L), TraceLine.Level.DEBUG, "Vision", "find ore → (150,80)", 3,
                "com.botmaker.sdk.api.vision.ImageFinder", "find", "com.example.Collect", OptionalInt.of(12),
                Optional.of(new TraceLine.Region(150, 80, 32, 24)))));
    }

    @Test
    void aTraceLineOfALevelTheHostDidNotKnowGoesOutWithAnEmptyLevel() {
        assertWire("trace.unknown", TelemetrySerializer.traceJson(new TraceLine(
                Instant.EPOCH, TraceLine.Level.UNKNOWN, "", "a line from a newer bot", 1, "", "", "",
                OptionalInt.empty(), Optional.empty())));
    }

    @Test
    void theRunStateMessageCarriesTheStateAndWhetherInputStaysOffTheHostsCursor() {
        assertWire("state.running.background", TelemetrySerializer.stateJson(RunState.RUNNING, true));
        assertWire("state.paused.foreground", TelemetrySerializer.stateJson(RunState.PAUSED, false));
        assertWire("state.stopped.background", TelemetrySerializer.stateJson(RunState.STOPPED, true));
    }

    @Test
    void aStateMessageCarriesAReasonOnlyWhenThereIsOne() {
        // The key is omitted rather than sent as null when there is no reason — which is why the three cases
        // above and this one are separate corpus entries rather than one shape with a nullable field.
        assertWire("state.stopped.reason", TelemetrySerializer.stateJson(RunState.STOPPED, true,
                "The background session is not showing any pixels on its X display."));
    }

    @Test
    void theVideoMessageCarriesTheCodecAndTheSurfaceRectTheStreamCovers() {
        // The rect lives here and not on each access unit: one encoder on one display means it changes only
        // when the stream does, and the client wants to hand the decoder a buffer with no header on it.
        assertWire("video.started", TelemetrySerializer.videoJson("avc1.42E01E", 0, 0, 1920, 1080));
    }

    @Test
    void aNullCodecIsHowAStreamEnds() {
        // Sent on every way a stream can end — route change, dead encoder, last H.264 client gone — because
        // the client has to tear its decoder down and start reading JPEG frames again for any of them.
        assertWire("video.stopped", TelemetrySerializer.videoStoppedJson());
    }

    @Test
    void theHeartbeatSaysNothingButItself() {
        assertWire("ping", TelemetrySerializer.pingJson());
    }

    // --- What the corpus cannot express ---

    @Test
    void theTimestampIsTheSendClock() {
        // 'ts' is stamped at serialization, not carried by the event, so the corpus holds a sentinel and
        // assertWire substitutes it. This is the assertion the substitution would otherwise throw away.
        long before = System.currentTimeMillis();
        JsonNode event = parse(TelemetrySerializer.telemetryJson(new RunEvent.Click(TARGET, 1, 2, 1)))
                .get("event");
        long ts = event.get("ts").asLong();
        assertTrue(ts >= before && ts <= System.currentTimeMillis(),
                "ts must be the wall clock at send time, was " + ts);
    }

    @Test
    void theSourceLineIsNotOnTheWire() {
        // Every RunEvent carries line(), and no message carries it out. Pinned so that the day the
        // pilot wants to show which block is running, this test is what says the field has to be added.
        JsonNode event = parse(TelemetrySerializer.telemetryJson(
                new RunEvent.Click(TARGET, 1, 2, 1, 42))).get("event");
        assertTrue(event.get("line") == null, "line() is dropped at the wire boundary");
    }

    @Test
    void aTitleWithAControlCharacterIsEscapedRatherThanBreakingTheMessage() {
        // B18, fixed by writing through Jackson: the hand-built writer escaped backslash and quote and
        // stopped there, and JSON forbids raw U+0000..U+001F in a string, so a newline in a title took the
        // whole message out — the client's JSON.parse threw and usePilot's catch dropped it silently.
        RunEvent.Target titled = new RunEvent.Target("Game\nWindow", 0, 0, 10, 10);
        JsonNode event = parse(TelemetrySerializer.telemetryJson(new RunEvent.Click(titled, 1, 2, 1)))
                .get("event");
        assertEquals("Game\nWindow", event.get("target").get("title").asText());
    }

    @Test
    void aNonFiniteConfidenceIsSentAsZero() {
        // B18's other half: %.4f of NaN was the bare token NaN, which is not JSON. A confidence that never
        // got computed is a miss's 0, which is a number the client can format.
        JsonNode event = parse(TelemetrySerializer.telemetryJson(
                new RunEvent.Match(TARGET, null, null, Double.NaN, false))).get("event");
        assertEquals(0.0, event.get("confidence").asDouble());
        assertTrue(event.get("confidence").isNumber());
    }

    @Test
    void aConfidenceIsRoundedToFourPlaces() {
        // What the old %.4f did, kept: the client shows four places, and fifteen digits of a float's
        // representation error are bytes on every match.
        JsonNode event = parse(TelemetrySerializer.telemetryJson(
                new RunEvent.Match(TARGET, null, null, 0.912345678, true))).get("event");
        assertEquals("0.9123", event.get("confidence").asText());
    }

    // --- The corpus itself ---

    @Test
    void theGoldenCorpusIsTheOneThePilotRepoHas() throws Exception {
        assertEquals(GOLDEN_SHA256, sha256(goldenBytes()),
                "pilot-wire/wire-golden.json changed. The pilot repo has a byte-identical copy and asserts the "
                        + "same digest — update both files and both constants, or the wire has silently forked.");
    }

    @AfterAll
    static void everyCaseInTheCorpusWasAsserted() {
        Set<String> uncovered = new TreeSet<>();
        for (Iterator<String> it = CORPUS.fieldNames(); it.hasNext(); ) {
            String name = it.next();
            // The rest of the corpus is the core's (its own TelemetryWireContractTest): the pilot moved there.
            if (name.startsWith("telemetry.") && !COVERED.contains(name)) uncovered.add(name);
        }
        assertEquals(Set.of(), uncovered, "telemetry cases with no SDK-side assertion");
    }

    // --- Helpers ---

    /** Compares an emitted message with its golden, ignoring only the send-clock timestamp. */
    private static void assertWire(String caseName, String actualJson) {
        JsonNode expected = CORPUS.get(caseName);
        assertNotNull(expected, "no such case in " + GOLDEN + ": " + caseName);
        JsonNode actual = parse(actualJson);
        if (actual.get("event") instanceof ObjectNode event && event.has("ts")) event.put("ts", 0);
        assertEquals(expected, actual, caseName + " no longer matches the corpus the pilot decodes");
        COVERED.add(caseName);
    }

    private static JsonNode parse(String json) {
        try {
            return JSON.readTree(json);
        } catch (JsonProcessingException e) {
            throw new AssertionError("the serializer emitted invalid JSON: " + json, e);
        }
    }

    private static JsonNode corpus() {
        try {
            return JSON.readTree(goldenBytes());
        } catch (IOException e) {
            throw new AssertionError("cannot read " + GOLDEN, e);
        }
    }

    private static byte[] goldenBytes() throws IOException {
        try (InputStream in = TelemetryWireContractTest.class.getResourceAsStream(GOLDEN)) {
            assertNotNull(in, GOLDEN + " is missing from the test resources");
            return in.readAllBytes();
        }
    }

    private static String sha256(byte[] bytes) throws NoSuchAlgorithmException {
        StringBuilder hex = new StringBuilder();
        for (byte b : MessageDigest.getInstance("SHA-256").digest(bytes)) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }
}
