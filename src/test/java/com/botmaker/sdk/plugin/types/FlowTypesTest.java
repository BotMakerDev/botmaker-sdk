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
 * with the shapes that broke every earlier encoding: a comma inside a description, a body named by method
 * reference, an off-wire and a home, and three container levels.
 *
 * <p>A body is kept exactly as the file writes it, a lambda included: this plugin checks nothing it reads.
 *
 * <h2>It asserted through {@code ValueCatalog} until 2026-09-22, and that was the wrong side of the line</h2>
 *
 * <p>The grammar is {@code botmaker-studio}'s, so the honest subject here is the pair this module declares:
 * {@code components} and {@code build}. The spell-and-split round trip is asserted where the speller lives.
 */
class FlowTypesTest {

    /** What the host hands over for {@code Activities.Collect.class} and {@code Activities.Collect.NOTHING_LEFT}. */
    private static final Activity COLLECT = BotConstants.activity("Collect");
    private static final Activity REST = BotConstants.activity("Rest");
    private static final Outcome NOTHING_LEFT = BotConstants.outcome("Collect", "NOTHING_LEFT");

    private static Flow gamebot() {
        Flow.Step collect = FlowTypes.step(COLLECT, FlowTypes.body("Collect::body"))
                .described("Click collect, then battle.").checksPopups();
        Flow.Step rest = FlowTypes.step(REST, FlowTypes.body("Rest::body"))
                .described("Wait, then go round again.").off().goesHome();
        rest = (Flow.Step) FlowTypes.STEP_SHAPE.withers().getLast().applyTo(rest, COLLECT);
        return new Flow(List.of(collect, rest),
                List.of(new Flow.Edge(BotConstants.outcome("Collect", "NEXT"), COLLECT),
                        new Flow.Edge(NOTHING_LEFT, REST),
                        new Flow.Edge(BotConstants.outcome("Rest", "NEXT"), COLLECT)),
                List.of(new Flow.Preset("Gathering only", List.of(COLLECT))),
                COLLECT, Flow.limits(1000, 1000), FlowTypes.home("Gamebot::goHome"));
    }

    /** {@code build(components(v))} for one shape, which is the law every {@code ComponentType} owes. */
    private static <T> T roundTrip(ComponentType<T> shape, T value) {
        return shape.build(shape.components(value));
    }

    @Test
    void aFlowComesApartAndGoesBackTogetherWhole() {
        assertEquals(gamebot(), roundTrip(FlowTypes.FLOW_SHAPE, gamebot()));
        assertEquals("Gamebot::goHome", FlowTypes.sourceOf(roundTrip(FlowTypes.FLOW_SHAPE, gamebot()).home()));
    }

    @Test
    void anEmptyFlowSurvives() {
        assertEquals(Flow.NONE, roundTrip(FlowTypes.FLOW_SHAPE, Flow.NONE));
        assertEquals(1, FlowTypes.FLOW_SHAPE.constants().size(), "written as Flow.NONE: no start to name");
        Flow emptied = new Flow(List.of(), List.of(), List.of(), null, Flow.limits(5, 5), null);
        assertEquals(Flow.NONE, FlowTypes.written(emptied), "a flow with no activity left has nothing to start at");
        assertEquals(gamebot(), FlowTypes.written(gamebot()));
    }

    /** Every nested shape owes the law too — a flow's parts are values, not text. */
    @Test
    void eachShapeInsideAFlowRoundTripsOnItsOwn() {
        Flow flow = gamebot();
        assertEquals(flow.steps().getFirst(), roundTrip(FlowTypes.STEP_SHAPE, flow.steps().getFirst()));
        assertEquals(flow.steps().getLast(), roundTrip(FlowTypes.STEP_SHAPE, flow.steps().getLast()));
        assertEquals(COLLECT, roundTrip(FlowTypes.STEP_SHAPE, flow.steps().getLast()).whenOff());
        assertNull(FlowTypes.STEP_SHAPE.build(List.of(COLLECT, "Collect::body", "a description")),
                "a part count that is neither the call's nor the call's and every link's is not this shape");
        assertEquals(flow.edges().get(1), roundTrip(FlowTypes.EDGE_SHAPE, flow.edges().get(1)));
        assertEquals(flow.presets().getFirst(), roundTrip(FlowTypes.PRESET_SHAPE, flow.presets().getFirst()));
        assertEquals(flow.limits(), roundTrip(FlowTypes.LIMITS_SHAPE, flow.limits()));
    }

    /** An activity is no part's {@code Class} the editor could hold: each shape refuses one that is not a name. */
    @Test
    void anActivityIsHeldAsItsName() {
        assertNull(FlowTypes.STEP_SHAPE.build(List.of(Outcome.class, "Collect::body")));
        assertNull(FlowTypes.EDGE_SHAPE.build(List.of(NOTHING_LEFT, Outcome.class)));
        assertNull(FlowTypes.STEP_SHAPE.build(List.of(Activity.NONE, "Collect::body")), "a step is of an activity");
    }

    /**
     * A flow is five components, and which five is the fact the host writes the call from: the start is a
     * {@code Class}, which the host reads and writes as the class literal of a managed enum.
     */
    @Test
    void aFlowIsFiveComponentsAndAHome() {
        assertEquals(List.of(List.class, List.class, List.class, Class.class, Flow.Limits.class, Runnable.class),
                FlowTypes.FLOW_SHAPE.componentTypes());
        assertEquals(6, FlowTypes.FLOW_SHAPE.components(gamebot()).size());
        assertEquals(List.of(Class.class, ActivityBody.class, String.class, boolean.class, boolean.class,
                boolean.class, Class.class), FlowTypes.STEP_SHAPE.componentTypes());
        assertEquals(List.of(Outcome.class, Class.class), FlowTypes.EDGE_SHAPE.componentTypes());
    }

    /** A flow is one call on {@code Flow} itself, which is what the host identifies it by. */
    @Test
    void aFlowIsWrittenAsACallOnFlow() throws NoSuchMethodException {
        assertEquals(Flow.class.getMethod("of", List.class, List.class, List.class, Class.class,
                Flow.Limits.class), FlowTypes.FLOW_SHAPE.factory());
    }

    /** An activity and an outcome are written by their names, which the host spells as the bot's own. */
    @Test
    void namesAreWrittenByName() throws ReflectiveOperationException {
        assertEquals(COLLECT, SdkTypes.ACTIVITY.build(SdkTypes.ACTIVITY.components(COLLECT)));
        assertEquals(NOTHING_LEFT, SdkTypes.OUTCOME.build(SdkTypes.OUTCOME.components(NOTHING_LEFT)));
        assertEquals(List.of(), SdkTypes.OUTCOME.constants(), "no outcome is the SDK's own");
        assertEquals(List.of(Activity.class.getField("NONE")), SdkTypes.ACTIVITY.constants());
    }

    /** A body the file writes is kept exactly as written, whatever it is; only the "none yet" call reads blank. */
    @Test
    void aBodyIsKeptAsWritten() {
        for (String written : List.of("Collect::body", "() -> Activities.Collect.NEXT")) {
            Flow.Step read = FlowTypes.STEP_SHAPE.build(List.of(COLLECT, written));
            assertEquals(written, FlowTypes.sourceOf(read.body()));
        }
        Flow.Step none = FlowTypes.STEP_SHAPE.build(List.of(COLLECT, "ActivityBody.none()"));
        assertEquals("", FlowTypes.sourceOf(none.body()));
        assertEquals("com.botmaker.sdk.api.flow.ActivityBody.none()",
                FlowTypes.STEP_SHAPE.components(none).get(1), "and is written back as the call that says so");
    }

    /** An activity whose body the editor read out of a file is a <em>name</em>, and running one says so. */
    @Test
    void aNamedBodyRefusesToRun() {
        FlowTypes.Named named = new FlowTypes.Named("Collect::body");
        assertEquals("Collect::body", FlowTypes.sourceOf(named));
        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class, named::run);
        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
                FlowTypes.home("Gamebot::goHome")::run);
    }

    @Test
    void anActivityWithNoBodyYetReadsAndWritesAsBlank() {
        assertEquals("", FlowTypes.sourceOf(FlowTypes.body("")));
        assertEquals("", FlowTypes.sourceOf(FlowTypes.body(null)));
        assertEquals("", FlowTypes.sourceOf(ActivityBody.none()));
        assertNull(FlowTypes.home(""), "no home is none, not a blank name");
    }

    /** The enable flag is part of the flow, because a run reads it. */
    @Test
    void anActivitySwitchedOffIsStillPartOfTheFlow() {
        Flow off = new Flow(List.of(FlowTypes.step(REST, FlowTypes.body("Rest::body")).off()), List.of(), List.of(),
                REST, Flow.Limits.DEFAULT, null);
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
