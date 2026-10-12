package com.botmaker.sdk.plugin.types;

import com.botmaker.plugin.api.value.ComponentType;
import com.botmaker.sdk.api.bot.Outcome;
import com.botmaker.sdk.api.flow.Activity;
import com.botmaker.sdk.api.flow.ActivityBody;
import com.botmaker.sdk.api.flow.Flow;
import com.botmaker.sdk.api.flow.FlowLayout;
import com.botmaker.sdk.api.flow.Run;
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
                .described("Wait, then go round again.").off().goesHome().switchable();
        rest = FlowTypes.whenOff(rest, COLLECT);
        return new Flow(List.of(collect, rest),
                List.of(new Flow.Edge(BotConstants.outcome("Collect", "NEXT"), COLLECT),
                        new Flow.Edge(NOTHING_LEFT, REST),
                        new Flow.Edge(BotConstants.outcome("Rest", "NEXT"), COLLECT)),
                COLLECT, Flow.limits(1000, 1000));
    }

    private static Run run() {
        return Run.of().home(FlowTypes.method("Gamebot::goHome")).setup(FlowTypes.method("Gamebot::setup"));
    }

    /** {@code build(components(v))} for one shape, which is the law every {@code ComponentType} owes. */
    private static <T> T roundTrip(ComponentType<T> shape, T value) {
        return shape.build(shape.components(value));
    }

    @Test
    void aFlowComesApartAndGoesBackTogetherWhole() {
        assertEquals(gamebot(), roundTrip(FlowTypes.FLOW_SHAPE, gamebot()));
        assertTrue(roundTrip(FlowTypes.FLOW_SHAPE, gamebot()).steps().getLast().runnerSwitch(),
                "the runner's switch rides on its card");
    }

    /**
     * The run's {@code .home(…)} and {@code .setup(…)} are two links on {@code Run.of()}, each written only when the
     * bot has one — one each for the whole bot, whichever flow runs.
     */
    @Test
    void theRunHoldsItsHomeAndItsSetup() {
        assertEquals(run(), roundTrip(FlowTypes.RUN_SHAPE, run()));
        assertEquals("Gamebot::goHome", FlowTypes.sourceOf(roundTrip(FlowTypes.RUN_SHAPE, run()).home()));
        Run none = run().setup(null);
        assertEquals("Gamebot::goHome", FlowTypes.sourceOf(none.home()), "dropping the setup keeps the home");
        assertNull(run().home(null).home());
        assertEquals("Gamebot::setup", FlowTypes.sourceOf(run().home(null).setup()), "and the other way round");
        assertEquals(2, FlowTypes.RUN_SHAPE.withers().size());
        assertEquals(List.of(Runnable.class, Runnable.class), FlowTypes.RUN_SHAPE.componentTypes());
        assertEquals(Run.NONE, roundTrip(FlowTypes.RUN_SHAPE, Run.NONE));
    }

    @Test
    void anEmptyFlowSurvives() {
        assertEquals(Flow.NONE, roundTrip(FlowTypes.FLOW_SHAPE, Flow.NONE));
        assertEquals(1, FlowTypes.FLOW_SHAPE.constants().size(), "written as Flow.NONE: no start to name");
        Flow emptied = new Flow(List.of(), List.of(), null, Flow.limits(5, 5));
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
        assertEquals(flow.limits(), roundTrip(FlowTypes.LIMITS_SHAPE, flow.limits()));
    }

    /** What the host hands over for {@code Flows.DAILY} and {@code Flows.WEEKLY} inside another flow. */
    private static final Flow DAILY = BotConstants.flow("DAILY");
    private static final Flow WEEKLY = BotConstants.flow("WEEKLY");

    /**
     * A flow running two others as cards, with every spelling a card, a wire and a start take when one end is a
     * sub-flow: {@code Flow.subflow(Flows.DAILY)}, a wire into it, out of it and between two of them, an off-wire
     * to one, and a flow starting at one.
     */
    private static Flow withSubflows() {
        Flow.Step daily = Flow.subflow(DAILY).described("The daily round.").switchable();
        Flow.Step weekly = FlowTypes.whenOff(Flow.subflow(WEEKLY).off(), new Flow.Sub(DAILY));
        Flow.Step rest = FlowTypes.whenOff(FlowTypes.step(REST, FlowTypes.body("Rest::body")), new Flow.Sub(WEEKLY));
        Outcome done = BotConstants.outcome("Claim", "DONE");
        return new Flow(List.of(daily, weekly, rest),
                List.of(new Flow.Edge(BotConstants.outcome("Rest", "NEXT"), new Flow.Sub(DAILY)),
                        new Flow.Edge(DAILY, done, REST),
                        new Flow.Edge(DAILY, BotConstants.outcome("Claim", "LATER"), new Flow.Sub(WEEKLY))),
                new Flow.Sub(DAILY), Flow.limits(1000, 1000));
    }

    /**
     * Each value is written by exactly one declaration of its class — the one whose {@code when} accepts it — and
     * comes apart and back together through it.
     */
    @Test
    void everySubflowSpellingIsOneDeclarationsAndRoundTrips() {
        Flow flow = withSubflows();
        assertEquals(flow, only(flow, FlowTypes.FLOW_SHAPE, FlowTypes.FLOW_FROM_SUBFLOW_SHAPE));
        for (Flow.Step step : flow.steps()) {
            assertEquals(step, only(step, FlowTypes.STEP_SHAPE, FlowTypes.SUBFLOW_STEP_SHAPE), step.toString());
        }
        for (Flow.Edge edge : flow.edges()) {
            assertEquals(edge, only(edge, FlowTypes.EDGE_SHAPE, FlowTypes.EDGE_INTO_SUBFLOW_SHAPE,
                    FlowTypes.EDGE_OUT_OF_SUBFLOW_SHAPE, FlowTypes.EDGE_BETWEEN_SUBFLOWS_SHAPE), edge.toString());
        }
        for (Flow.Edge edge : gamebot().edges()) {
            assertEquals(edge, only(edge, FlowTypes.EDGE_SHAPE, FlowTypes.EDGE_INTO_SUBFLOW_SHAPE,
                    FlowTypes.EDGE_OUT_OF_SUBFLOW_SHAPE, FlowTypes.EDGE_BETWEEN_SUBFLOWS_SHAPE), edge.toString());
        }
        assertEquals(gamebot(), only(gamebot(), FlowTypes.FLOW_SHAPE, FlowTypes.FLOW_FROM_SUBFLOW_SHAPE));
    }

    /** {@code value} through the one of {@code shapes} that writes it, failing when not exactly one does. */
    @SafeVarargs
    private static <T> T only(T value, ComponentType<T>... shapes) {
        List<ComponentType<T>> writing = java.util.Arrays.stream(shapes).filter(shape -> shape.writes(value)).toList();
        assertEquals(1, writing.size(), "declarations writing " + value);
        return roundTrip(writing.getFirst(), value);
    }

    /** A reference to a flow is its name: two references to one flow are one card, whatever that flow holds. */
    @Test
    void aSubflowCardIsTheFlowItNames() {
        assertEquals(new Flow.Sub(BotConstants.flow("DAILY")), new Flow.Sub(DAILY));
        assertEquals(new Flow.Sub(BotConstants.flow("DAILY")).hashCode(), new Flow.Sub(DAILY).hashCode());
        assertFalse(new Flow.Sub(DAILY).equals(new Flow.Sub(WEEKLY)));
        assertEquals("DAILY", BotConstants.flowName(DAILY));
        assertEquals("DAILY", new Flow.Sub(DAILY).name());
        assertNull(BotConstants.flowName(Flow.NONE), "an empty flow is no reference");
        assertNull(BotConstants.flowName(gamebot()));
    }

    /** An activity is no part's {@code Class} the editor could hold: each shape refuses one that is not a name. */
    @Test
    void anActivityIsHeldAsItsName() {
        assertNull(FlowTypes.STEP_SHAPE.build(List.of(Outcome.class, "Collect::body")));
        assertNull(FlowTypes.EDGE_SHAPE.build(List.of(NOTHING_LEFT, Outcome.class)));
        assertNull(FlowTypes.STEP_SHAPE.build(List.of(Activity.NONE, "Collect::body")), "a step is of an activity");
    }

    /**
     * A flow is four components, and which four is the fact the host writes the call from: the start is a
     * {@code Class}, which the host reads and writes as the class literal of a managed enum.
     */
    @Test
    void aFlowIsFourComponents() {
        assertEquals(List.of(List.class, List.class, Class.class, Flow.Limits.class),
                FlowTypes.FLOW_SHAPE.componentTypes());
        assertEquals(4, FlowTypes.FLOW_SHAPE.components(gamebot()).size());
        assertEquals(List.of(Class.class, ActivityBody.class, String.class, boolean.class, boolean.class,
                boolean.class, boolean.class, Class.class, Flow.class), FlowTypes.STEP_SHAPE.componentTypes());
        assertEquals(List.of(Outcome.class, Class.class), FlowTypes.EDGE_SHAPE.componentTypes());
    }

    /** A flow is one call on {@code Flow} itself, which is what the host identifies it by. */
    @Test
    void aFlowIsWrittenAsACallOnFlow() throws NoSuchMethodException {
        assertEquals(Flow.class.getMethod("of", List.class, List.class, Class.class, Flow.Limits.class),
                FlowTypes.FLOW_SHAPE.factory());
        assertEquals(Run.class.getMethod("of"), FlowTypes.RUN_SHAPE.factory());
    }

    /** An activity and an outcome are written by their names, which the host spells as the bot's own. */
    @Test
    void namesAreWrittenByName() throws ReflectiveOperationException {
        assertEquals(COLLECT, SdkTypes.ACTIVITY.build(SdkTypes.ACTIVITY.components(COLLECT)));
        assertEquals(NOTHING_LEFT, SdkTypes.OUTCOME.build(SdkTypes.OUTCOME.components(NOTHING_LEFT)));
        assertEquals(List.of(Outcome.class.getField("NONE")), SdkTypes.OUTCOME.constants(),
                "the SDK's one outcome is none, what a fresh one is written as");
        assertEquals(Outcome.NONE, SdkTypes.OUTCOME.fresh());
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
                FlowTypes.method("Gamebot::goHome")::run);
    }

    @Test
    void anActivityWithNoBodyYetReadsAndWritesAsBlank() {
        assertEquals("", FlowTypes.sourceOf(FlowTypes.body("")));
        assertEquals("", FlowTypes.sourceOf(FlowTypes.body(null)));
        assertEquals("", FlowTypes.sourceOf(ActivityBody.none()));
        assertNull(FlowTypes.method(""), "no home is none, not a blank name");
    }

    /** The enable flag is part of the flow, because a run reads it. */
    @Test
    void anActivitySwitchedOffIsStillPartOfTheFlow() {
        Flow off = new Flow(List.of(FlowTypes.step(REST, FlowTypes.body("Rest::body")).off()), List.of(), REST,
                Flow.Limits.DEFAULT);
        Flow read = roundTrip(FlowTypes.FLOW_SHAPE, off);
        assertFalse(read.steps().getFirst().enabled());
        assertEquals(off, read);
    }

    /**
     * The card positions come back as they were written, one arrangement per flow, each in the order its cards
     * were given, so the Java the editor writes does not reshuffle on every save.
     */
    @Test
    void aLayoutRoundTripsPerFlowInItsOwnOrder() {
        Map<String, FlowLayout.Spot> main = new LinkedHashMap<>();
        main.put("Rest", FlowLayout.at(320, 80));
        main.put("Collect", FlowLayout.at(40, 120));
        Map<String, FlowLayout.Cards> flows = new LinkedHashMap<>();
        flows.put("MAIN", FlowLayout.cards(main));
        flows.put("DAILY", FlowLayout.cards(Map.of("Collect", FlowLayout.at(0, 0))));
        FlowLayout layout = FlowLayout.of(flows, false);

        FlowLayout read = FlowTypes.LAYOUT_SHAPE.build(FlowTypes.LAYOUT_SHAPE.components(layout).stream()
                .map(part -> part instanceof Map<?, ?> m ? rebuiltCards(m) : part).toList());
        assertEquals(layout, read);
        assertEquals(List.of("MAIN", "DAILY"), List.copyOf(read.flows().keySet()));
        assertEquals(List.of("Rest", "Collect"), List.copyOf(read.cardsOf("MAIN").spots().keySet()));
        assertFalse(read.goHomeByDefault());
        assertEquals(FlowLayout.at(40, 120), read.cardsOf("MAIN").spot("Collect"));
        assertEquals(FlowLayout.at(0, 0), read.cardsOf("DAILY").spot("Collect"), "the same card, placed apart");
        assertTrue(read.cardsOf("WEEKLY").spots().isEmpty(), "a flow nobody laid out has no cards placed");
    }

    /** Replacing one flow's cards keeps every other flow's where it was. */
    @Test
    void oneFlowsCardsChangeAlone() {
        FlowLayout layout = FlowLayout.of(Map.of("DAILY", FlowLayout.cards(Map.of("Collect", FlowLayout.at(1, 2)))),
                true);
        FlowLayout moved = layout.with("MAIN", FlowLayout.cards(Map.of("Rest", FlowLayout.at(5, 6))));
        assertEquals(FlowLayout.at(1, 2), moved.cardsOf("DAILY").spot("Collect"));
        assertEquals(FlowLayout.at(5, 6), moved.cardsOf("MAIN").spot("Rest"));
        assertFalse(moved.goingHomeByDefault(false).goHomeByDefault());
    }

    /** A layout nobody drew is written as the constant that says so. */
    @Test
    void anEmptyLayoutIsWrittenAsNone() throws ReflectiveOperationException {
        assertEquals(List.of(FlowLayout.class.getField("NONE")), FlowTypes.LAYOUT_SHAPE.constants());
        assertTrue(FlowLayout.NONE.flows().isEmpty());
        assertTrue(FlowLayout.NONE.goHomeByDefault());
    }

    /** Each flow's cards and each spot through their own declarations, as the host takes a map's values apart. */
    private static Map<String, FlowLayout.Cards> rebuiltCards(Map<?, ?> written) {
        Map<String, FlowLayout.Cards> out = new LinkedHashMap<>();
        written.forEach((name, cards) -> out.put((String) name, FlowTypes.CARDS_SHAPE.build(
                FlowTypes.CARDS_SHAPE.components((FlowLayout.Cards) cards).stream()
                        .map(part -> part instanceof Map<?, ?> m ? rebuiltSpots(m) : part).toList())));
        return out;
    }

    private static Map<String, FlowLayout.Spot> rebuiltSpots(Map<?, ?> written) {
        Map<String, FlowLayout.Spot> out = new LinkedHashMap<>();
        written.forEach((name, spot) -> out.put((String) name,
                FlowTypes.SPOT_SHAPE.build(FlowTypes.SPOT_SHAPE.components((FlowLayout.Spot) spot))));
        return out;
    }
}
