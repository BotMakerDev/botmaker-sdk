package com.botmaker.sdk.plugin.types;

import com.botmaker.plugin.api.value.ComponentType;
import com.botmaker.sdk.api.bot.Outcome;
import com.botmaker.sdk.api.flow.Activity;
import com.botmaker.sdk.api.flow.ActivityBody;
import com.botmaker.sdk.api.flow.Flow;
import com.botmaker.sdk.api.flow.FlowLayout;
import com.botmaker.sdk.internal.flow.BotConstants;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A flow taken apart and put back together.
 *
 * <p><b>{@code build(components(v))} equals {@code v} is the whole claim</b>, and it is asserted over a flow
 * with the shapes that broke every earlier encoding: a comma inside a description, an activity with no
 * outcomes, a body named by method reference, and three container levels (a flow holding a list of
 * activities each holding a list of outcomes).
 *
 * <p>A body is kept exactly as the file writes it, a lambda included: this plugin checks nothing it reads.
 *
 * <h2>It asserted through {@code ValueCatalog} until 2026-09-22, and that was the wrong side of the line</h2>
 *
 * <p>It used to spell the whole call — {@code CATALOG.initializer(FLOW, flow)} — and split it back with
 * {@code valueOf}, which asserted the SDK's shapes and the <em>host's grammar</em> in one breath. The
 * grammar is {@code botmaker-studio}'s now, because nothing outside a host ever walked one, so from here
 * the honest subject is the pair this module actually declares: {@code components} and {@code build}. The
 * spell-and-split round trip is asserted where the speller lives.
 *
 * <p>Nothing about the claim weakened. A part that {@code components} hands over wrong is still caught, and
 * it is caught without a second module's writer having to agree first.
 */
class FlowTypesTest {

    private static final Activity COLLECT = BotConstants.activity("COLLECT");
    private static final Activity REST = BotConstants.activity("REST");
    private static final Outcome NOTHING_LEFT = BotConstants.outcome("NOTHING_LEFT");

    private static Flow gamebot() {
        return Flow.of(
                List.of(Flow.activity(COLLECT, new FlowTypes.Named("Collect::body"))
                                .described("Click collect, then battle.").checksPopups().reports(List.of(NOTHING_LEFT)),
                        Flow.activity(REST, new FlowTypes.Named("Rest::body"))
                                .described("Wait, then go round again.").off().goesHome()),
                List.of(Flow.edge(COLLECT, COLLECT, Outcome.NEXT),
                        Flow.edge(COLLECT, REST, NOTHING_LEFT),
                        Flow.edge(REST, COLLECT, Outcome.NEXT)),
                List.of(Flow.preset("Gathering only", List.of(COLLECT))),
                COLLECT,
                Flow.limits(1000, 1000));
    }

    /** {@code build(components(v))} for one shape, which is the law every {@code ComponentType} owes. */
    private static <T> T roundTrip(ComponentType<T> shape, T value) {
        return shape.build(shape.components(value));
    }

    @Test
    void aFlowComesApartAndGoesBackTogetherWhole() {
        assertEquals(gamebot(), roundTrip(FlowTypes.FLOW_SHAPE, gamebot()));
    }

    @Test
    void anEmptyFlowSurvives() {
        assertEquals(Flow.NONE, roundTrip(FlowTypes.FLOW_SHAPE, Flow.NONE));
    }

    /** Every nested shape owes the law too — a flow's parts are values, not text. */
    @Test
    void eachShapeInsideAFlowRoundTripsOnItsOwn() {
        Flow flow = gamebot();
        assertEquals(flow.steps().getFirst(), roundTrip(FlowTypes.STEP_SHAPE, flow.steps().getFirst()));
        assertEquals(flow.steps().getLast(), roundTrip(FlowTypes.STEP_SHAPE, flow.steps().getLast()));
        assertNull(FlowTypes.STEP_SHAPE.build(List.of(COLLECT, "Collect::body", "a description")),
                "a part count that is neither the call's nor the call's and every link's is not this shape");
        // The activity with no outcomes: an empty list is a value, not an absent one.
        assertEquals(flow.steps().get(1), roundTrip(FlowTypes.STEP_SHAPE, flow.steps().get(1)));
        assertEquals(flow.edges().getFirst(), roundTrip(FlowTypes.EDGE_SHAPE, flow.edges().getFirst()));
        assertEquals(flow.presets().getFirst(),
                roundTrip(FlowTypes.PRESET_SHAPE, flow.presets().getFirst()));
        assertEquals(flow.limits(), roundTrip(FlowTypes.LIMITS_SHAPE, flow.limits()));
    }

    /**
     * A flow is five components, and which five is the fact the host writes the call from.
     *
     * <p>Asserted by class rather than by a rendered type name: the host takes the name off the object, and
     * a list crosses as {@code List.class} because that is all its erasure can say. What the elements of
     * each list are is answered by the {@code ComponentType} whose own prefix the host reads them back with.
     */
    @Test
    void aFlowIsFiveComponents() {
        assertEquals(List.of(List.class, List.class, List.class, Activity.class, Flow.Limits.class),
                FlowTypes.FLOW_SHAPE.componentTypes());
        assertEquals(5, FlowTypes.FLOW_SHAPE.components(gamebot()).size());
    }

    /** A flow is one call on {@code Flow} itself, which is what the host identifies it by. */
    @Test
    void aFlowIsWrittenAsACallOnFlow() throws NoSuchMethodException {
        assertEquals(Flow.class.getMethod("of", List.class, List.class, List.class, Activity.class,
                Flow.Limits.class), FlowTypes.FLOW_SHAPE.factory());
    }

    /**
     * An activity and an outcome are their label, written {@code X.named("…")} — which the host replaces with the
     * bot's constant holding the same label — and the two outcomes every activity has are written as theirs.
     */
    @Test
    void namesAreWrittenAsTheirLabelOrTheSdksConstant() throws ReflectiveOperationException {
        assertEquals(COLLECT, SdkTypes.ACTIVITY.build(SdkTypes.ACTIVITY.components(COLLECT)));
        assertEquals(NOTHING_LEFT, SdkTypes.OUTCOME.build(SdkTypes.OUTCOME.components(NOTHING_LEFT)));
        assertEquals(List.of(Outcome.class.getField("NEXT"), Outcome.class.getField("DISABLED")),
                SdkTypes.OUTCOME.constants());
        assertEquals(List.of(Activity.class.getField("NONE")), SdkTypes.ACTIVITY.constants());
    }

    /** A body the file writes is kept exactly as written, whatever it is; only the "none yet" constant reads blank. */
    @Test
    void aBodyIsKeptAsWritten() {
        for (String written : List.of("Collect::body", "() -> Outcome.NEXT")) {
            Flow.Step read = FlowTypes.STEP_SHAPE.build(
                    List.of(COLLECT, written, "", true, false, false, List.of()));
            assertEquals(written, FlowTypes.sourceOf(read.body()));
        }
        Flow.Step none = FlowTypes.STEP_SHAPE.build(
                List.of(COLLECT, "ActivityBody.NONE", "", true, false, false, List.of()));
        assertEquals("", FlowTypes.sourceOf(none.body()));
    }

    /**
     * An activity whose body the editor read out of a file is a <em>name</em>, and running one says so.
     *
     * <p>Rather than doing nothing: a flow the editor assembled was never meant to run, and a body that
     * silently returned would be a bot walking its flow reporting nothing.
     */
    @Test
    void aNamedBodyRefusesToRun() {
        FlowTypes.Named named = new FlowTypes.Named("Collect::body");
        assertEquals("Collect::body", named.source());
        assertEquals("Collect::body", FlowTypes.sourceOf(named));
        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
                named::run);
    }

    /**
     * A card drawn but not written yet round-trips as the constant that says so.
     *
     * <p>The alternative was writing a blank where the method reference goes, which is a file that does not
     * compile — and the reason this had to be sayable at all is that drawing the flow first is an ordinary
     * way to work.
     */
    @Test
    void anActivityWithNoBodyYetReadsAndWritesAsBlank() {
        assertEquals("", FlowTypes.sourceOf(FlowTypes.body("")));
        assertEquals("", FlowTypes.sourceOf(FlowTypes.body(null)));
        // A live body has nothing to spell it back as, which is the same blank the editor reads as
        // "no body named yet" -- and what the host writes for it is ActivityBody.NONE.
        assertEquals("", FlowTypes.sourceOf(ActivityBody.NONE));
    }

    /** The enable flag is part of the flow, because a run reads it. */
    @Test
    void anActivitySwitchedOffIsStillPartOfTheFlow() {
        Flow off = Flow.of(List.of(Flow.activity(REST, new FlowTypes.Named("Rest::body")).off()),
                List.of(), List.of(), REST, Flow.Limits.DEFAULT);
        Flow read = roundTrip(FlowTypes.FLOW_SHAPE, off);
        assertFalse(read.steps().getFirst().enabled());
        assertEquals(off, read);
    }

    /** A saved preset is a named set of enable flags, and it travels in the value beside them. */
    @Test
    void aPresetRoundTripsWithTheFlow() {
        Flow read = roundTrip(FlowTypes.FLOW_SHAPE, gamebot());
        assertEquals(1, read.presets().size());
        assertEquals("Gathering only", read.presets().getFirst().name());
        assertTrue(read.presets().getFirst().enables(COLLECT));
        assertFalse(read.presets().getFirst().enables(REST));
    }

    /**
     * The card positions come back as they were written, in the order the cards were given, so the Java the
     * editor writes does not reshuffle on every save.
     */
    @Test
    void aLayoutRoundTripsInItsOwnOrder() {
        Map<String, FlowLayout.Spot> spots = new LinkedHashMap<>();
        spots.put("Rest", FlowLayout.at(320, 80));
        spots.put("Collect", FlowLayout.at(40, 120));
        FlowLayout layout = FlowLayout.of(spots, false);

        FlowLayout read = FlowTypes.LAYOUT_SHAPE.build(FlowTypes.LAYOUT_SHAPE.components(layout).stream()
                .map(part -> part instanceof Map<?, ?> m ? rebuiltSpots(m) : part).toList());
        assertEquals(layout, read);
        assertEquals(List.of("Rest", "Collect"), List.copyOf(read.spots().keySet()));
        assertFalse(read.goHomeByDefault());
        assertEquals(FlowLayout.at(40, 120), read.spot("Collect"));
    }

    /** A layout nobody drew is written as the constant that says so. */
    @Test
    void anEmptyLayoutIsWrittenAsNone() throws ReflectiveOperationException {
        assertEquals(List.of(FlowLayout.class.getField("NONE")), FlowTypes.LAYOUT_SHAPE.constants());
        assertTrue(FlowLayout.NONE.spots().isEmpty());
        assertTrue(FlowLayout.NONE.goHomeByDefault());
    }

    /** Each spot through its own declaration, as the host takes a map's values apart. */
    private static Map<String, FlowLayout.Spot> rebuiltSpots(Map<?, ?> written) {
        Map<String, FlowLayout.Spot> out = new LinkedHashMap<>();
        written.forEach((name, spot) -> out.put((String) name,
                FlowTypes.SPOT_SHAPE.build(FlowTypes.SPOT_SHAPE.components((FlowLayout.Spot) spot))));
        return out;
    }
}
