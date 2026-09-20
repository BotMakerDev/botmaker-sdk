package com.botmaker.sdk.internal.authoring;

import com.botmaker.plugin.api.value.ValueCatalog;
import com.botmaker.plugin.api.value.ValueForm;
import com.botmaker.plugin.basics.values.BasicsValueTypes;
import com.botmaker.sdk.api.flow.Flow;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A flow written into a bot's Java and read back out of it.
 *
 * <p><b>{@code read(write(v)) == v} is the whole claim</b>, and it is asserted over a flow with the shapes
 * that broke every earlier encoding: a comma inside a description, an activity with no outcomes, a body
 * named by method reference, and three container levels (a flow holding a list of activities each holding a
 * list of outcomes).
 *
 * <p>The other half is what must <b>not</b> read back: an activity whose body is a lambda, or a capture
 * source narrowed with {@code region(…)}. Both are code somebody wrote on purpose, both come back empty, and
 * the editor then shows that value read-only rather than replacing it.
 */
class SdkFlowValuesTest {

    private static final ValueCatalog CATALOG =
            BasicsValueTypes.CATALOG.merge(SdkValueTypes.CATALOG);

    private static final ValueForm FLOW = new ValueForm.Of(SdkFlowValues.FLOW_SHAPE, List.of());
    private static final ValueForm BODY = ValueForm.of(SdkFlowValues.ACTIVITY_BODY);
    private static final ValueForm SOURCE = ValueForm.of(SdkFlowValues.CAPTURE_SOURCE);

    private static Flow gamebot() {
        return Flow.of(
                List.of(Flow.activity(new SdkFlowValues.Named("Collect::body"), "Collect",
                                "Click collect, then battle.", true, false, true, List.of("NOTHING_LEFT")),
                        Flow.activity(new SdkFlowValues.Named("Rest::body"), "Rest",
                                "Wait, then go round again.", false, true, false, List.of())),
                List.of(Flow.edge("Collect", "Collect", ""),
                        Flow.edge("Collect", "Rest", "NOTHING_LEFT"),
                        Flow.edge("Rest", "Collect", "")),
                List.of(Flow.preset("Gathering only", List.of("Collect"))),
                "Collect",
                Flow.limits(1000, 1000));
    }

    @Test
    void aFlowIsWrittenAsOneExpressionAndReadBackWhole() {
        String java = CATALOG.initializer(FLOW, gamebot()).orElseThrow();

        assertTrue(java.startsWith("com.botmaker.sdk.api.flow.Flow.of("), java);
        assertTrue(java.contains("Collect::body"), java);
        // A comma inside a description is inside a string literal, and the split reads it as one part.
        assertTrue(java.contains("\"Click collect, then battle.\""), java);

        assertEquals(gamebot(), CATALOG.valueOf(FLOW, java).orElseThrow());
    }

    @Test
    void anEmptyFlowSurvives() {
        String java = CATALOG.initializer(FLOW, Flow.NONE).orElseThrow();
        assertEquals(Flow.NONE, CATALOG.valueOf(FLOW, java).orElseThrow());
    }

    /** A fixed shape takes no type arguments and is written without brackets. */
    @Test
    void theFormSpellsItselfWithoutAngleBrackets() {
        assertEquals("com.botmaker.sdk.api.flow.Flow", FLOW.sourceName());
        assertEquals(0, SdkFlowValues.FLOW_SHAPE.arity());
        assertTrue(FLOW.known());
        // Nothing to type values *of*: a fixed shape has parts, not a type argument.
        assertEquals(null, FLOW.leaf());
    }

    @Test
    void aBodyIsAMethodReferenceAndNothingElse() {
        assertEquals("Collect::body", CATALOG.valueOf(BODY, "Collect::body").orElseThrow());
        assertEquals("com.mybot.Collect::body",
                CATALOG.valueOf(BODY, "com.mybot.Collect::body").orElseThrow());

        // Code somebody wrote: kept, shown, never replaced.
        assertTrue(CATALOG.valueOf(BODY, "ctx -> ctx.done()").isEmpty());
        assertTrue(CATALOG.valueOf(BODY, "bodyFor(\"Collect\")").isEmpty());
        assertTrue(CATALOG.valueOf(BODY, "Collect").isEmpty());
    }

    @Test
    void aCaptureSourceIsOneOfItsOwnThreeFactories() {
        for (String java : List.of("CaptureSource.desktop()", "CaptureSource.monitor(1)",
                "CaptureSource.window(\"Game\")",
                "com.botmaker.sdk.api.capture.CaptureSource.desktop()")) {
            assertEquals(java, CATALOG.valueOf(SOURCE, java).orElseThrow(), java);
        }

        // Narrowings compose, so a picker could show the first and not the second. Read-only is honest.
        assertTrue(CATALOG.valueOf(SOURCE, "CaptureSource.desktop().region(top)").isEmpty());
        assertTrue(CATALOG.valueOf(SOURCE, "mySource()").isEmpty());
    }

    /**
     * An activity whose body the editor read out of a file is a <em>name</em>, and running one says so.
     *
     * <p>Rather than doing nothing: a flow the editor assembled was never meant to run, and a body that
     * silently returned would be a bot walking its flow reporting nothing.
     */
    @Test
    void aNamedBodyRefusesToRun() {
        SdkFlowValues.Named named = new SdkFlowValues.Named("Collect::body");
        assertEquals("Collect::body", named.source());
        assertFalse(CATALOG.initializer(BODY, named.source()).isEmpty());
        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
                () -> named.run(null));
    }

    @Test
    void thePartsOfAFlowAreTypedAllTheWayDown() {
        String java = CATALOG.initializer(FLOW, gamebot()).orElseThrow();
        List<ValueCatalog.Part> parts = CATALOG.partsOfInitializer(FLOW, java).orElseThrow();

        assertEquals(5, parts.size());
        assertEquals("java.util.List<com.botmaker.sdk.api.flow.Flow.Activity>",
                parts.getFirst().form().sourceName());
        assertEquals("java.util.List<com.botmaker.sdk.api.flow.Flow.Edge>", parts.get(1).form().sourceName());
        assertEquals("java.util.List<com.botmaker.sdk.api.flow.Flow.Preset>",
                parts.get(2).form().sourceName());
        assertEquals("String", parts.get(3).form().sourceName());
        assertEquals("com.botmaker.sdk.api.flow.Flow.Limits", parts.get(4).form().sourceName());
    }

    /**
     * A card drawn but not written yet round-trips as the constant that says so.
     *
     * <p>The alternative was writing a blank where the method reference goes, which is a file that does not
     * compile — and the reason this had to be sayable at all is that drawing the flow first is an ordinary
     * way to work.
     */
    @Test
    void anActivityWithNoBodyYetIsWrittenAsTheConstantForOne() {
        String java = CATALOG.initializer(BODY, "").orElseThrow();
        assertEquals("com.botmaker.sdk.api.bot.ActivityBody.NONE", java);
        assertEquals("", CATALOG.valueOf(BODY, java).orElseThrow());
        assertEquals("", CATALOG.valueOf(BODY, "ActivityBody.NONE").orElseThrow());
    }

    /** The enable flag is part of the flow, because a run reads it. */
    @Test
    void anActivitySwitchedOffIsStillWrittenIntoTheFlow() {
        Flow off = Flow.of(List.of(Flow.activity(new SdkFlowValues.Named("Rest::body"), "Rest", "",
                        false, false, false, List.of())),
                List.of(), List.of(), "Rest", Flow.Limits.DEFAULT);
        String java = CATALOG.initializer(FLOW, off).orElseThrow();
        Flow read = (Flow) CATALOG.valueOf(FLOW, java).orElseThrow();
        assertFalse(read.activities().getFirst().enabled());
        assertEquals(off, read);
    }

    /** A saved preset is a named set of enable flags, and it travels in the value beside them. */
    @Test
    void aPresetRoundTripsWithTheFlow() {
        Flow read = (Flow) CATALOG.valueOf(FLOW, CATALOG.initializer(FLOW, gamebot()).orElseThrow())
                .orElseThrow();
        assertEquals(1, read.presets().size());
        assertEquals("Gathering only", read.presets().getFirst().name());
        assertTrue(read.presets().getFirst().enables("Collect"));
        assertFalse(read.presets().getFirst().enables("Rest"));
    }
}
